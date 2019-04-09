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

interface CheckpointStep {

  fun name(): String {
    return this::class.java.simpleName
  }

  /**
   * Verifies whether or not the step needs to be run, returning any cloud state necessary for subsequent steps to
   * complete successfully.
   */
  fun verify(priorOperationInputs: List<Any>, priorStepInputs: List<StepInputs>, description: Any): VerifyResult

  /**
   * TODO(rz): Make StepInputs contain all prior inputs, description, etc as well?
   */
  fun <T> run(stepLog: MutableList<StepLog>,
              priorInputs2: StepInputs2<T>): StepOutput
}

data class VerifyResult(
  val action: VerifyAction,
  // TODO(rz): Cast to StepInputs?
  val workspace: Map<String, Any>
) {

  enum class VerifyAction {
    RUN,
    SKIP,
    ABORT
  }
}
