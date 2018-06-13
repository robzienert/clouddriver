/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.aws.deploy.ops

import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.CreateTagsRequest
import com.amazonaws.services.ec2.model.DeleteTagsRequest
import com.amazonaws.services.ec2.model.DescribeTagsRequest
import com.amazonaws.services.ec2.model.DescribeTagsResult
import com.amazonaws.services.ec2.model.Filter
import com.amazonaws.services.ec2.model.LaunchPermission
import com.amazonaws.services.ec2.model.LaunchPermissionModifications
import com.amazonaws.services.ec2.model.ModifyImageAttributeRequest
import com.amazonaws.services.ec2.model.Tag
import com.amazonaws.services.ec2.model.TagDescription
import com.netflix.spinnaker.clouddriver.aws.deploy.AmiIdResolver
import com.netflix.spinnaker.clouddriver.aws.deploy.ResolvedAmiResult
import com.netflix.spinnaker.clouddriver.aws.deploy.description.AllowLaunchDescription
import com.netflix.spinnaker.clouddriver.aws.model.AwsResultsRetriever
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.kork.core.RetrySupport
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired

class AllowLaunchAtomicOperation implements AtomicOperation<ResolvedAmiResult> {
  private static final String BASE_PHASE = "ALLOW_LAUNCH"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AllowLaunchDescription description

  AllowLaunchAtomicOperation(AllowLaunchDescription description) {
    this.description = description
  }

  @Autowired
  AmazonClientProvider amazonClientProvider

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  RetrySupport retrySupport

  @Override
  ResolvedAmiResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing Allow Launch Operation..."

    def sourceCredentials = description.credentials
    def targetCredentials = accountCredentialsProvider.getCredentials(description.account) as NetflixAmazonCredentials
    def sourceAmazonEC2 = amazonClientProvider.getAmazonEC2(description.credentials, description.region, true)
    def targetAmazonEC2 = amazonClientProvider.getAmazonEC2(targetCredentials, description.region, true)


    task.updateStatus BASE_PHASE, "Looking up AMI imageId '$description.amiName' in target accountId='$targetCredentials.accountId'"
    ResolvedAmiResult resolvedAmi = AmiIdResolver.resolveAmiIdFromAllSources(targetAmazonEC2, description.region, description.amiName, targetCredentials.accountId)

    boolean existsInTarget = false
    if (!resolvedAmi) {
      task.updateStatus BASE_PHASE, "Looking up AMI imageId '$description.amiName' in source accountId='$description.credentials.accountId'"
      resolvedAmi = AmiIdResolver.resolveAmiIdFromAllSources(sourceAmazonEC2, description.region, description.amiName, description.credentials.accountId)
    } else {
      existsInTarget = true
    }

    if (!resolvedAmi && targetCredentials.allowPrivateThirdPartyImages) {
      resolvedAmi = AmiIdResolver.resolveAmiId(targetAmazonEC2, description.region, description.amiName)
      if (resolvedAmi) {
        task.updateStatus BASE_PHASE, "AMI appears to be from a private third-party, which is permitted on this target account: skipping allow launch"
        return resolvedAmi
      }
    }

    if (!resolvedAmi) {
      throw new IllegalArgumentException("unable to resolve AMI imageId from '$description.amiName': If this is a private AMI owned by a third-party, you will need to contact them to share the AMI to your desired account(s)")
    }

    // If the AMI is public, this is a no-op
    if (resolvedAmi.isPublic) {
      task.updateStatus BASE_PHASE, "AMI is public, no need to allow launch"
      return resolvedAmi
    }

    // If the AMI was created/owned by a different account, switch to using that for modifying the image
    if (resolvedAmi.ownerId != sourceCredentials.accountId) {
      if (resolvedAmi.getRegion())
      sourceCredentials = accountCredentialsProvider.all.find { accountCredentials ->
        accountCredentials instanceof NetflixAmazonCredentials &&
          ((AmazonCredentials) accountCredentials).accountId == resolvedAmi.ownerId
      } as NetflixAmazonCredentials
      if (!sourceCredentials) {
        throw new IllegalArgumentException("Unable to find owner of resolved AMI $resolvedAmi")
      }
      sourceAmazonEC2 = amazonClientProvider.getAmazonEC2(sourceCredentials, description.region, true)
    }

    if (existsInTarget) {
      task.updateStatus BASE_PHASE, "AMI found in target account: skipping allow launch"
    } else {
      task.updateStatus BASE_PHASE, "Allowing launch of $description.amiName from $description.account"

      OperationPoller.retryWithBackoff({ o ->
        sourceAmazonEC2.modifyImageAttribute(new ModifyImageAttributeRequest().withImageId(resolvedAmi.amiId).withLaunchPermission(
          new LaunchPermissionModifications().withAdd(new LaunchPermission().withUserId(targetCredentials.accountId))))
      }, 500, 3)
    }

    if (sourceCredentials == targetCredentials) {
      task.updateStatus BASE_PHASE, "Tag replication not required"
    } else {
      def request = new DescribeTagsRequest().withFilters(new Filter("resource-id").withValues(resolvedAmi.amiId))
      Closure<Set<Tag>> getTags = { DescribeTagsRequest req, TagsRetriever ret ->
        new HashSet<Tag>(ret.retrieve(req).collect { new Tag(it.key, it.value) })
      }.curry(request)
      Set<Tag> sourceTags = getTags(new TagsRetriever(sourceAmazonEC2, retrySupport))
      if (sourceTags.isEmpty()) {
        Thread.sleep(200)
        sourceTags = getTags(new TagsRetriever(sourceAmazonEC2, retrySupport))
      }
      if (sourceTags.isEmpty()) {
        task.updateStatus BASE_PHASE, "WARNING: empty tag set returned from DescribeTags, skipping tag sync"
      } else {

        Set<Tag> targetTags = getTags(new TagsRetriever(targetAmazonEC2, retrySupport))

        Set<Tag> tagsToRemoveFromTarget = new HashSet<>(targetTags)
        tagsToRemoveFromTarget.removeAll(sourceTags)
        Set<Tag> tagsToAddToTarget = new HashSet<>(sourceTags)
        tagsToAddToTarget.removeAll(targetTags)

        if (tagsToRemoveFromTarget) {
          task.updateStatus BASE_PHASE, "Removing tags on target AMI (${tagsToRemoveFromTarget.collect { "${it.key}: ${it.value}" }.join(", ")})."
          retrySupport.retry({
            targetAmazonEC2.deleteTags(new DeleteTagsRequest().withResources(resolvedAmi.amiId).withTags(tagsToRemoveFromTarget))
          }, 3, 500, false)
        }
        if (tagsToAddToTarget) {
          task.updateStatus BASE_PHASE, "Creating tags on target AMI (${tagsToAddToTarget.collect { "${it.key}: ${it.value}" }.join(", ")})."
          retrySupport.retry({
            targetAmazonEC2.createTags(new CreateTagsRequest().withResources(resolvedAmi.amiId).withTags(tagsToAddToTarget))
          }, 3, 500, false)
        }
      }
    }

    task.updateStatus BASE_PHASE, "Done allowing launch of $description.amiName from $description.account."
    resolvedAmi
  }

  @Canonical
  static class TagsRetriever extends AwsResultsRetriever<TagDescription, DescribeTagsRequest, DescribeTagsResult> {
    final AmazonEC2 amazonEC2
    final RetrySupport retrySupport

    @Override
    protected DescribeTagsResult makeRequest(DescribeTagsRequest request) {
      retrySupport.retry({ amazonEC2.describeTags(request) }, 3, 500, false)
    }

    @Override
    protected List<TagDescription> accessResult(DescribeTagsResult result) {
      result.tags
    }
  }
}
