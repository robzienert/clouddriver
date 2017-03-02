/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy

import com.amazonaws.auth.policy.Condition
import com.amazonaws.auth.policy.Policy
import com.amazonaws.auth.policy.Principal
import com.amazonaws.auth.policy.Resource
import com.amazonaws.auth.policy.Statement
import com.amazonaws.auth.policy.Statement.Effect
import com.amazonaws.auth.policy.actions.SNSActions
import com.amazonaws.auth.policy.actions.SQSActions
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.PutLifecycleHookRequest
import com.amazonaws.services.autoscaling.model.PutNotificationConfigurationRequest
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.GetTopicAttributesResult
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.GetQueueAttributesResult
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.model.AmazonAsgLifecycleHook
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator
import com.netflix.spinnaker.clouddriver.data.task.Task
import groovy.transform.Canonical

import java.util.regex.Pattern

@Canonical
class AsgLifecycleHookWorker {

  private final static REGION_TEMPLATE_PATTERN = Pattern.quote("{{region}}")
  private final static ACCOUNT_ID_TEMPLATE_PATTERN = Pattern.quote("{{accountId}}")

  private final static MANAGED_POLICY_STATEMENT_ID = 'spinnaker-managed-statement'

  final AmazonClientProvider amazonClientProvider

  final NetflixAmazonCredentials targetCredentials
  final String targetRegion

  IdGenerator idGenerator

  void attach(Task task, List<AmazonAsgLifecycleHook> lifecycleHooks, String targetAsgName) {
    if (lifecycleHooks?.size() == 0) {
      return
    }

    def targetAccountId = targetCredentials.accountId
    AmazonAutoScaling autoScaling = amazonClientProvider.getAutoScaling(targetCredentials, targetRegion, true)
    lifecycleHooks.each { lifecycleHook ->
      String lifecycleHookName = lifecycleHook.name ?: [targetAsgName, 'lifecycle', idGenerator.nextId()].join('-')

      switch (lifecycleHook.lifecycleTransition.type) {
        case AmazonAsgLifecycleHook.TransitionType.LIFECYCLE:
          def request = new PutLifecycleHookRequest(
            autoScalingGroupName: targetAsgName,
            lifecycleHookName: cleanLifecycleHookName(lifecycleHookName),
            roleARN: arnTemplater(lifecycleHook.roleARN, targetRegion, targetAccountId),
            notificationTargetARN: arnTemplater(lifecycleHook.notificationTargetARN, targetRegion, targetAccountId),
            notificationMetadata: lifecycleHook.notificationMetadata,
            lifecycleTransition: lifecycleHook.lifecycleTransition.toString(),
            heartbeatTimeout: lifecycleHook.heartbeatTimeout,
            defaultResult: lifecycleHook.defaultResult.toString()
          )
          autoScaling.putLifecycleHook(request)

          task.updateStatus "AWS_DEPLOY", "Creating lifecycle hook (${request}) on ${targetRegion}/${targetAsgName}"
          break

        case AmazonAsgLifecycleHook.TransitionType.NOTIFICATION:
          def request = new PutNotificationConfigurationRequest()
            .withAutoScalingGroupName(targetAsgName)
            .withNotificationTypes(lifecycleHook.lifecycleTransition.toString())
            .withTopicARN(arnTemplater(lifecycleHook.notificationTargetARN, targetRegion, targetAccountId))
          autoScaling.putNotificationConfiguration(request)

          task.updateStatus "AWS_DEPLOY", "Creating notification hook (${request}) on ${targetRegion}/${targetAsgName}"
          break
      }
    }
  }

  private static String arnTemplater(String arnTemplate, String region, String accountId) {
    arnTemplate.replaceAll(REGION_TEMPLATE_PATTERN, region).replaceAll(ACCOUNT_ID_TEMPLATE_PATTERN, accountId)
  }

  void ensureNotificationInfraExists(Task task, List<AmazonAsgLifecycleHook> lifecycleHooks, String region, String targetAsgName) {
    Names names = Names.parseName(targetAsgName)
    String topicAndQueueName = "spinnaker-autoscalingNotifications-${names.cluster}"

    def targetAccountId = targetCredentials.accountId

    def topicArn = snsTopicArn(targetAccountId, targetRegion, topicAndQueueName)
    def queueArn = sqsQueueArn(targetAccountId, targetRegion, topicAndQueueName)

    AmazonSQS amazonSQS = amazonClientProvider.getAmazonSQS(targetCredentials, region)
    AmazonSNS amazonSNS = amazonClientProvider.getAmazonSNS(targetCredentials, region)

    def transitions = lifecycleHooks.collect { it.lifecycleTransition.value }
    task.updateStatus "AWS_DEPLOY", "Creating lifecycle notification SNS topic and SQS queue (${topicAndQueueName}) on ${targetRegion}/${targetAsgName} for ${transitions.join(",")}"

    // It's a huge rabbit hole to offer SNS & SQS attribute configuration to the user,
    // so instead we'll just ensure that the base requirements are setup amidst
    // potential external changes. This model allows service teams to manage their own
    // permissions and specific topic & queue behaviors directly as they see fit.
    String queueUrl = amazonSQS.createQueue(topicAndQueueName).queueUrl
    ensureBaseSQSPermissions(amazonSQS, queueUrl, queueArn, topicArn)

    amazonSNS.createTopic(topicAndQueueName)
    ensureBaseSNSPermissions(amazonSNS, topicArn, targetAccountId)
    amazonSNS.subscribe(topicArn, "sqs", queueArn)

    lifecycleHooks.each { lifecycleHook ->
      lifecycleHook.notificationTargetARN = topicArn
    }
  }

  private static void ensureBaseSQSPermissions(AmazonSQS amazonSQS, String queueUrl, String queueArn, String topicArn) {
    GetQueueAttributesResult attributesResult = amazonSQS.getQueueAttributes(queueUrl, Collections.singletonList("Policy"))

    String policyJson = attributesResult.attributes["Policy"]
    Policy policy = (policyJson == null) ? new Policy() : Policy.fromJson(policyJson)

    policy.statements.removeIf { it.id == MANAGED_POLICY_STATEMENT_ID }
    policy.statements.add(new Statement(Effect.Allow)
      .withId(MANAGED_POLICY_STATEMENT_ID)
      .withActions(SQSActions.SendMessage)
      .withPrincipals(Principal.All)
      .withResources(new Resource(queueArn))
      .withConditions(new Condition().withType("ArnEquals").withConditionKey("aws:SourceArn").withValues(topicArn))
    )

    amazonSQS.setQueueAttributes(queueUrl, Collections.singletonMap("Policy", policy.toJson()))
  }

  private static void ensureBaseSNSPermissions(AmazonSNS amazonSNS, String topicArn, String targetAccountId) {
    GetTopicAttributesResult attributesResult = amazonSNS.getTopicAttributes(topicArn)

    String policyJson = attributesResult.attributes["Policy"]
    Policy policy = (policyJson == null) ? new Policy() : Policy.fromJson(policyJson)

    policy.statements.removeIf { it.id == MANAGED_POLICY_STATEMENT_ID }
    policy.statements.add(new Statement(Effect.Allow)
      .withId(MANAGED_POLICY_STATEMENT_ID)
      .withActions(SNSActions.Publish)
      .withPrincipals(new Principal(targetAccountId))
      .withResources(new Resource(topicArn))
    )

    amazonSNS.setTopicAttributes(topicArn, "Policy", policy.toJson())
  }

  public static String cleanLifecycleHookName(String name) {
    return name.replaceAll("[^A-Za-z0-9\\-_/]", "_")
  }

  private static String snsTopicArn(String account, String region, String name) {
    def partition = region.startsWith("cn-") ? "aws-cn" : "aws"
    return "arn:${partition}:sns:${region}:${account}:${name}"
  }

  private static String sqsQueueArn(String account, String region, String name) {
    def partition = region.startsWith("cn-") ? "aws-cn" : "aws"
    return "arn:${partition}:sqs:${region}:${account}:${name}"
  }
}
