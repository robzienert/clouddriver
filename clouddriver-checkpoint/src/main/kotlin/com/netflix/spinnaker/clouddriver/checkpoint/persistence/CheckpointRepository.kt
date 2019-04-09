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
 * Persistence interface for checkpointed operations.
 *
 * TODO(rz): Should pivot this to be just for diagnostics. Keep clouddriver stateless. Enforce verifiers to
 * return cloud state?
 */
interface CheckpointRepository {

  /**
   * Commit a new OperationState.
   *
   * This is an upsert operation, and is expected to enforce last-write-wins conflict resolution, however
   * operations should try not to update state in parallel.
   */
  fun commit(state: OperationState)

  /**
   * Get the OperationState by [id].
   */
  fun get(id: String): OperationState?

  /**
   * Cleanup all OperationStates that are older than [olderThan].
   */
  fun cleanup(olderThan: Instant)
}
