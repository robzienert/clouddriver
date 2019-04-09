/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.checkpoint

import java.time.Instant
import java.util.UUID

/**
 * Runtime transient state for an AtomicOperation invocation.
 *
 * This state is runtime transient and should not be persisted except in diagnostics cases.
 */
data class OperationState(
  val correlationId: String,
  val invocationId: String = UUID.randomUUID().toString(),
  val steps: MutableList<StepOutput> = mutableListOf()
) {

  val id = "$correlationId:$invocationId"

  fun createdAt(): Instant {
    return steps.sortedBy { it.startedAt }.firstOrNull()?.startedAt ?: Instant.now()
  }

  fun lastUpdatedAt(): Instant {
    return steps.sortedBy { it.completedAt }.firstOrNull()?.completedAt ?: Instant.now()
  }
}
