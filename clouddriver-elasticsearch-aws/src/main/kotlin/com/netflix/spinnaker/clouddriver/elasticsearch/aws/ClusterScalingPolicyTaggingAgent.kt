/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.elasticsearch.aws

import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.elasticsearch.ElasticSearchClient
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.clouddriver.tags.EntityTagger.ENTITY_TYPE_CLUSTER
import com.netflix.spinnaker.kork.core.RetrySupport
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private const val REGISTRY_ID_PREFIX = "agents.clusterScalingPolicyTagging"

class ClusterScalingPolicyTaggingAgent (
  val retrySupport: RetrySupport,
  val registry: Registry,
  val amazonClientProvider: AmazonClientProvider,
  val accounts: Collection<NetflixAmazonCredentials>,
  val elasticSearchClient: ElasticSearchClient,
  val entityTagger : EntityTagger,
  val objectMapper: ObjectMapper
) : RunnableAgent, CustomScheduledAgent {
  private val log = LoggerFactory.getLogger(ElasticSearchAmazonInstanceCachingAgent::class.java)

  private val missingScalingPolicyCounterId = registry.createId("$REGISTRY_ID_PREFIX.missing")
  private val updatedScalingPolicyCounterId = registry.createId("$REGISTRY_ID_PREFIX.updated")

  override fun getPollIntervalMillis(): Long {
    return TimeUnit.SECONDS.toMillis(10)
  }

  override fun getTimeoutMillis(): Long {
    return TimeUnit.SECONDS.toMillis(30)
  }

  override fun getAgentType(): String {
    return ClusterScalingPolicyTaggingAgent::class.java.simpleName
  }

  override fun getProviderName(): String {
    return AwsProvider.PROVIDER_NAME
  }

  override fun run() {
    for (credentials in accounts) {
      for (region in credentials.regions) {
        val amazonAutoScaling = amazonClientProvider.getAutoScaling(credentials, region.name)

        val scalingPolicies = amazonAutoScaling.describePolicies().scalingPolicies
        val scalingPoliciesByServerGroupName = scalingPolicies.groupBy { it.autoScalingGroupName }
        val scalingPoliciesByCluster = mutableMapOf<String, MutableList<ScalingPolicy>>()

        val serverGroups = amazonAutoScaling.describeAutoScalingGroups().autoScalingGroups

        for (serverGroup in serverGroups) {
          val cluster = Names.parseName(serverGroup.autoScalingGroupName).cluster

          val scalingPoliciesForCluster = scalingPoliciesByCluster.getOrDefault(cluster, mutableListOf())
          scalingPoliciesForCluster.addAll(
            scalingPoliciesByServerGroupName.getOrDefault(serverGroup.autoScalingGroupName, emptyList())
          )

          if (scalingPoliciesForCluster.isNotEmpty()) {
            scalingPoliciesByCluster[cluster] = scalingPoliciesForCluster
          }
        }

        if (scalingPoliciesByCluster.isNotEmpty()) {
          println("${credentials.name} - ${region.name} - ${scalingPoliciesByCluster.size}")
        }

        val entityTagsByCluster = entityTagger.taggedEntities(
          "aws", credentials.accountId, "cluster", "spinnaker:scaling_policies", 2000
        ).groupBy { it.entityRef.entityId }

        val serverGroupsMissingScalingPolicies = serverGroups.filter {
          val cluster = Names.parseName(it.autoScalingGroupName).cluster

          scalingPoliciesByServerGroupName.containsKey(it.autoScalingGroupName) && !entityTagsByCluster.containsKey(cluster)
        }

        // maybe emit a metric and log the server groups missing scaling policies
        // if the server group has been around for awhile, maybe just delete the tag and consider it the new norm

        for ((cluster, scalingPoliciesForCluster) in scalingPoliciesByCluster) {
          // probably worth a de-dupe to save unnecesssary updates

          entityTagger.tag(
            "aws",
            credentials.accountId,
            region.name,
            "spinnaker",
            ENTITY_TYPE_CLUSTER,
            cluster,
            "spinnaker:scaling_policies",
            objectMapper.writeValueAsString(scalingPoliciesForCluster),
            System.currentTimeMillis()
          )
        }
      }
    }
  }

  private fun groupByServerGroupName(scalingPolicies: List<ScalingPolicy>): Map<String, List<ScalingPolicy>> {
    TODO()
  }

  private fun groupByCluster(scalingPolicies: List<ScalingPolicy>): Map<String, List<ScalingPolicy>> {
    TODO()
  }
}
