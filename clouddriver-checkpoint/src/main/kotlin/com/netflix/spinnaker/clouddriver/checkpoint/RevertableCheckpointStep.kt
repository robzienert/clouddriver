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

/**
 * TODO(rz): Figure out how reverts would work...
 *
 * Since we want to keep clouddriver stateless, steps will need to be capable of understanding directionality:
 * Are we rolling forward or backwards? It could be that "revertable" steps are just composites steps: One forward
 * and one back?
 */
interface RevertableCheckpointStep<T : StepInputs, S> : CheckpointStep {

  fun revert(inputs: T, createdState: S): StepOutput
}

//class RevertableCheckpointStep(
//  private val forward: CheckpointStep,
//  private val backward: CheckpointStep
//) : CheckpointStep {
//
//  override fun name(): String {
//    return "${super.name()}[${forward.name()},${backward.name()}]"
//  }
//
//  override fun verify(priorOperationInputs: List<Any>, priorStepInputs: List<StepInputs>, description: Any): VerifyResult {
//    throw UnsupportedOperationException("not implemented")
//  }
//
//  override fun run(stepLog: MutableList<StepLog>, priorOperationInputs: List<Any>, priorStepInputs: List<StepInputs>, description: Any): StepOutput {
//    throw UnsupportedOperationException("not implemented")
//  }
//}
