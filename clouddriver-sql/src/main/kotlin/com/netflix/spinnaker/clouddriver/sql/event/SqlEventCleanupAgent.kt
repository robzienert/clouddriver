/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.sql.event

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.RunnableAgent
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import com.netflix.spinnaker.clouddriver.core.provider.CoreProvider
import com.netflix.spinnaker.clouddriver.sql.transactional
import com.netflix.spinnaker.config.ConnectionPools
import com.netflix.spinnaker.config.SqlEventCleanupAgentConfigProperties
import com.netflix.spinnaker.kork.sql.routing.withPool
import io.github.resilience4j.retry.RetryRegistry
import org.jooq.DSLContext
import org.jooq.impl.DSL.currentTimestamp
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.jooq.impl.DSL.timestampDiff
import org.jooq.types.DayToSecond
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * Cleans up [SpinnakerEvent]s (by [Aggregate]) that are older than a configured number of days.
 */
class SqlEventCleanupAgent(
  private val jooq: DSLContext,
  private val registry: Registry,
  private val properties: SqlEventCleanupAgentConfigProperties,
  private val retryRegistry: RetryRegistry
) : RunnableAgent, CustomScheduledAgent {

  private val log by lazy { LoggerFactory.getLogger(javaClass) }

  private val deletedId = registry.createId("sql.eventCleanupAgent.deleted")
  private val timingId = registry.createId("sql.eventCleanupAgent.timing")

  override fun run() {
    val duration = Duration.ofDays(properties.maxAggregateAgeDays)
    val cutoff = Instant.now().minus(duration)
    log.info("Deleting aggregates last updated earlier than $cutoff ($duration)")

    withPool(ConnectionPools.EVENTS.value) {
      jooq.transactional(retryRegistry.retry(RETRY_CONFIG_NAME)) { ctx ->

        val deleted = registry.timer(timingId).record<Int> {
          ctx.deleteFrom(table("event_aggregates"))
            .where(
              timestampDiff(field("last_change_timestamp", Timestamp::class.java), currentTimestamp())
                .greaterThan(DayToSecond.valueOf(duration))
            )
            .execute()
        }
        registry.counter(deletedId).increment(deleted.toLong())
        log.info("Deleted $deleted event aggregates")
      }
    }
  }

  override fun getAgentType(): String = javaClass.simpleName
  override fun getProviderName(): String = CoreProvider.PROVIDER_NAME
  override fun getPollIntervalMillis() = properties.frequency.toMillis()
  override fun getTimeoutMillis() = properties.timeout.toMillis()

  companion object {
    private val RETRY_CONFIG_NAME = "eventCleanupAgent"
  }
}
