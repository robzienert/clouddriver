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

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.ScalingPolicy
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials
import com.netflix.spinnaker.clouddriver.elasticsearch.aws.ClusterScalingPolicyTaggingAgent.Companion.LIST_OF_MAPS
import com.netflix.spinnaker.clouddriver.model.EntityTags
import com.netflix.spinnaker.clouddriver.tags.EntityTagger
import com.netflix.spinnaker.config.ClusterScalingPolicyTaggingAgentProperties
import com.netflix.spinnaker.kork.core.RetrySupport
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue
import java.time.Duration
import java.time.Instant
import java.util.*

class ClusterScalingPolicyTaggingAgentSpec : Spek({

  val retrySupport = RetrySupport()
  val registry = NoopRegistry()
  val objectMapper = ObjectMapper()

  val amazonClientProvider = mock<AmazonClientProvider>()
  val entityTagger = mock<EntityTagger>()

  fun resetMocks() {
    reset(amazonClientProvider, entityTagger)
  }

  describe("running agent for a single account region") {
    val subject = ClusterScalingPolicyTaggingAgent(
      retrySupport,
      registry,
      amazonClientProvider,
      listOf(),
      entityTagger,
      objectMapper,
      ClusterScalingPolicyTaggingAgentProperties()
    )

    val credentials = mock<NetflixAmazonCredentials>()
    val region = mock<AmazonCredentials.AWSRegion>()

    beforeGroup {
      whenever(credentials.name) doReturn "test"
      whenever(credentials.accountId) doReturn "1234"
      whenever(region.name) doReturn "us-west-2"
    }

    afterGroup {
      resetMocks()
      reset(credentials, region)
    }

    given("no scaling policies exist") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("orca-test-v000")
      )

      val accountRegion = AccountRegionIndex(
        credentials = credentials,
        region = region,
        serverGroups = serverGroups,
        scalingPolicies = AccountRegionScalingPolicies(
          all = listOf(),
          byServerGroupName = mapOf(),
          byCluster = mapOf()
        )
      )

      whenever(entityTagger.taggedEntities(any(), any(), any(), any(), any())) doReturn listOf<EntityTags>()

      on("poll cycle") {
        subject.forAccountRegion(accountRegion)

        it("does not tag any entities") {
          verify(entityTagger, times(1)).taggedEntities("aws", "1234", "cluster", "spinnaker:scaling_policies", 2000)
          verifyNoMoreInteractions(entityTagger)
        }
      }
    }

    given("all server groups are up to date") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("orca-test-v000")
      )
      val clouddriverPolicy = scalingPolicy {
        it.policyARN = "arn-a"
        it.autoScalingGroupName = "clouddriver-test-v000"
      }
      val orcaPolicy = scalingPolicy {
        it.policyARN = "arn-b"
        it.autoScalingGroupName = "orca-test-v000"
      }

      val accountRegion = AccountRegionIndex(
        credentials = credentials,
        region = region,
        serverGroups = serverGroups,
        scalingPolicies = AccountRegionScalingPolicies(
          all = listOf(clouddriverPolicy, orcaPolicy),
          byServerGroupName = mapOf(
            "clouddriver-test-v000" to listOf(clouddriverPolicy),
            "orca-test-v000" to listOf(orcaPolicy)
          ),
          byCluster = mapOf(
            "clouddriver-test" to listOf(clouddriverPolicy),
            "orca-test" to listOf(orcaPolicy)
          )
        )
      )

      whenever(entityTagger.taggedEntities(any(), any(), any(), any(), any())) doReturn listOf<EntityTags>()

      on("poll cycle") {
        subject.forAccountRegion(accountRegion)

        it("does nothing") {
          verify(entityTagger, times(2)).taggedEntities("aws", "1234", "cluster", "spinnaker:scaling_policies", 2000)
          verifyNoMoreInteractions(entityTagger)
        }
      }
    }

    given("server groups with scaling policies and no cluster entity tags") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("orca-test-v000")
      )
      val clouddriverPolicy1 = scalingPolicy {
        it.policyARN = "arn-a"
        it.autoScalingGroupName = "clouddriver-test-v000"
      }
      val clouddriverPolicy2 = scalingPolicy {
        it.policyARN = "arn-a"
        it.autoScalingGroupName = "clouddriver-test-v001"
      }
      val orcaPolicy = scalingPolicy {
        it.policyARN = "arn-b"
        it.autoScalingGroupName = "orca-test-v000"
      }

      val accountRegion = AccountRegionIndex(
        credentials = credentials,
        region = region,
        serverGroups = serverGroups,
        scalingPolicies = AccountRegionScalingPolicies(
          all = listOf(clouddriverPolicy1, clouddriverPolicy2, orcaPolicy),
          byServerGroupName = mapOf(
            "clouddriver-test-v000" to listOf(clouddriverPolicy1),
            "clouddriver-test-v001" to listOf(clouddriverPolicy2),
            "orca-test-v000" to listOf(orcaPolicy)
          ),
          byCluster = mapOf(
            "clouddriver-test" to listOf(clouddriverPolicy1, clouddriverPolicy2),
            "orca-test" to listOf(orcaPolicy)
          )
        )
      )

      whenever(entityTagger.taggedEntities(any(), any(), any(), any(), any())) doReturn listOf<EntityTags>()

      on("poll cycle") {
        subject.forAccountRegion(accountRegion)

        it("saves cluster scaling policies entity tags") {

          verify(entityTagger, times(1)).tag(
            eq("aws"),
            eq("1234"),
            eq("us-west-2"),
            eq("spinnaker"),
            eq("cluster"),
            eq("clouddriver-test"),
            eq("spinnaker:scaling_policies"),
            eq(objectMapper.convertValue(listOf(clouddriverPolicy1), LIST_OF_MAPS)),
            any()
          )
          verify(entityTagger, times(1)).tag(
            eq("aws"),
            eq("1234"),
            eq("us-west-2"),
            eq("spinnaker"),
            eq("cluster"),
            eq("orca-test"),
            eq("spinnaker:scaling_policies"),
            eq(objectMapper.convertValue(listOf(orcaPolicy), LIST_OF_MAPS)),
            any()
          )
          verifyNoMoreInteractions(entityTagger)
        }
      }
    }
  }

  describe("get scaling policies by cluster") {
    val subject = ClusterScalingPolicyTaggingAgent(
      retrySupport,
      registry,
      amazonClientProvider,
      listOf(),
      entityTagger,
      objectMapper,
      ClusterScalingPolicyTaggingAgentProperties()
    )

    given("multiple server groups in cluster") {
      val serverGroups = listOf(
        serverGroup("clouddriver-test-v000"),
        serverGroup("clouddriver-test-v001")
      )
      val scalingPoliciesByServerGroupName = mapOf(
        "clouddriver-test-v000" to listOf(
          scalingPolicy { it.withPolicyARN("arn-a") },
          scalingPolicy { it.withPolicyARN("arn-b") },
          scalingPolicy { it.withPolicyARN("arn-c") }
        ),
        "clouddriver-test-v001" to listOf(
          scalingPolicy { it.withPolicyARN("arn-b") },
          scalingPolicy { it.withPolicyARN("arn-c") }
        )
      )

      on("method call") {
        val result = subject.getScalingPoliciesByCluster(serverGroups, scalingPoliciesByServerGroupName)

        it("de-duplicates policies by arn") {
          expect(result["clouddriver-test"])
            .isNotNull()
            .isA<List<ScalingPolicy>>()
            .map("get scaling policy arns") { map { it.policyARN } }
            .assert("no duplicates exist") {
              if (this.subject == listOf("arn-a", "arn-b", "arn-c")) {
                pass()
              } else {
                fail()
              }
            }
        }
      }
    }
  }

  describe("shouldUpdateClusterEntityTags method") {
    val subject = ClusterScalingPolicyTaggingAgent(
      retrySupport,
      registry,
      amazonClientProvider,
      listOf(),
      entityTagger,
      objectMapper,
      ClusterScalingPolicyTaggingAgentProperties()
    )

    val credentials = mock<NetflixAmazonCredentials>()
    val region = mock<AmazonCredentials.AWSRegion>()

    beforeGroup {
      whenever(credentials.name) doReturn "test"
      whenever(credentials.accountId) doReturn "1234"
      whenever(region.name) doReturn "us-west-2"
    }

    afterGroup {
      resetMocks()
      reset(credentials, region)
    }

    on("an old server group") {
      val serverGroup = serverGroup("clouddriver-test-v000") {
        it.createdTime = Date.from(Instant.now().minus(Duration.ofDays(30)))
      }
      val boundary = Instant.now()

      it("should update entity tags") {
        expect(subject.shouldUpdateClusterEntityTags(serverGroup, credentials, region, boundary)) {
          isTrue()
        }
      }
    }

    on("a new server group") {
      val serverGroup = serverGroup("clouddriver-test-v000") {
        it.createdTime = Date.from(Instant.now())
      }
      val boundary = Instant.now().minus(Duration.ofHours(1))

      it("should not update entity tags") {
        expect(subject.shouldUpdateClusterEntityTags(serverGroup, credentials, region, boundary)) {
          isFalse()
        }
      }
    }
  }
})

private fun serverGroup(name: String): AutoScalingGroup {
  return serverGroup(name, null)
}

private fun serverGroup(name: String, fn: ((AutoScalingGroup) -> Unit)?): AutoScalingGroup {
  return AutoScalingGroup().apply {
    withAutoScalingGroupName(name)
    if (fn != null) {
      fn(this)
    }
  }
}

private fun scalingPolicy(fn: (ScalingPolicy) -> Unit): ScalingPolicy {
  return ScalingPolicy().apply(fn)
}
