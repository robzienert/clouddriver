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

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.clouddriver.checkpoint.persistence.InMemoryCheckpointRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe

object CheckpointSystemTests : Spek({

  describe("api experimenting") {
    val processor = DefaultCheckpointProcessor(InMemoryCheckpointRepository(), NoopRegistry())

    processor.process(
      "id",
      listOf(),
      listOf(),
      SampleDescription("clouddriver-main", listOf("us-west-2", "us-east-1"))
    )
  }
})

internal data class SampleDescription(
  val clusterName: String,
  val regions: List<String>
)

internal class SampleAtomicOperation(
  val correlationId: String,
  val description: SampleDescription,
  val processor: CheckpointProcessor,
  val someBackend: SomeBackend
) : AtomicOperation<Void> {
  override fun operate(priorOutputs: MutableList<Any?>): Void {
    processor.process(
      correlationId,
      listOf(
        object : CheckpointStep {
          override val name = "alwaysRunStep"

          override fun <T> run(stepLog: MutableList<StepLog>, priorInputs: StepInputs<T>): StepOutput {

            throw UnsupportedOperationException("not implemented")
          }
        },
        object : CheckpointStep {
          override val name = "verifyBeforeRunStep"

          override fun <T> verify(inputs: StepInputs<T>): VerifyResult {
            throw UnsupportedOperationException("not implemented")
          }

          override fun <T> run(stepLog: MutableList<StepLog>, priorInputs: StepInputs<T>): StepOutput {
            throw UnsupportedOperationException("not implemented")
          }
        }
      ),
      // TODO(rz): oops... hmm...
      priorOutputs.toList() as List<Any>,
      description
    )
  }
}

class SomeBackend(val state: Map<String, String>) {
  fun getState(region: String): String? {
    return state[region]
  }

  fun
}
