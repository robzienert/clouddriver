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
package com.netflix.spinnaker.clouddriver.checkpoint.persistence

import com.netflix.spinnaker.clouddriver.checkpoint.OperationState
import java.time.Instant

/**
 * For use in test cases.
 */
class InMemoryCheckpointRepository : CheckpointRepository {

  private val states: MutableMap<String, OperationState> = mutableMapOf()

  override fun commit(state: OperationState) {
    states[state.id] = state
  }

  override fun get(id: String): OperationState? {
    return states[id]
  }

  override fun cleanup(olderThan: Instant) {
    throw UnsupportedOperationException("not implemented")
  }
}
