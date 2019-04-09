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

data class StepLog(
  val id: String = UUID.randomUUID().toString(),
  val data: Map<String, Any> = emptyMap(),
  val createdAt: Instant = Instant.now(),

  /**
   * Label, userNotes and operatorNotes are all documentation-related and Allow attaching notes about state changes
   * based on the consumer.
   *
   * The thought is that we would have the option of variable verbosity for both operators and end-users on-demand via
   * API (rather than logs), and we'd have diff notes based on consumer (our end users, or us?).
   */
  val label: String,
  val userNotes: String?,
  val operatorNotes: String?,

  // TODO(rz): StepErrorLog?
  val cause: Throwable? = null,
  val errorData: Map<String, Any>? = null
)
