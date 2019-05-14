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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.checkpoint.VerifyResult.VerifyAction.ABORT
import com.netflix.spinnaker.clouddriver.checkpoint.VerifyResult.VerifyAction.SKIP
import com.netflix.spinnaker.clouddriver.checkpoint.exceptions.AbortedCheckpointStepException
import com.netflix.spinnaker.clouddriver.checkpoint.persistence.CheckpointRepository
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Offers a stateless checkpoint processor.
 *
 * TODO(rz): metrics
 */
class DefaultCheckpointProcessor(
  private val repository: CheckpointRepository,
  private val registry: Registry
) : CheckpointProcessor {

  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * TODO(rz): Annotation processor for documenting ids?
   * @metricTag action The verification action associated with the step
   */
  private val stepVerifyDurationId = registry.createId("checkpoint.step.verify.duration")
  private val stepRunDurationId = registry.createId("checkpoint.step.run.duration")
  private val stepInvocationsId = registry.createId("checkpoint.step.invocations")

  override fun <T : Any> process(correlationId: String, steps: List<CheckpointStep>, priorInputs: List<Any>, description: T) {
    log.trace("$correlationId: Starting new operation")

    val state = OperationState(correlationId)

    val stepInputs = StepInputs(priorInputs, mutableListOf(), description)
    for (step in steps) {
      log.trace("${state.id}: Starting step ${step.name}")
      // TODO(rz): Verify should be conditional
      val result = step.verify(stepInputs)
      log.trace("${state.id}: Step verification result: ${result.action}")

      stepInputs.priorSteps.add(result.workspace)

      if (result.action == SKIP) {
        log.debug("${state.id}: Skipping step ${step.name}")
        continue
      }

      // TODO(rz): Throwing here will make it more ugly to do processor metrics
      if (result.action == ABORT) {
        log.warn("${state.id}: Aborting step ${step.name}")
        registry.counter(stepInvocationsId.withTag("action", ABORT.toString())).increment()
        throw AbortedCheckpointStepException("Operation was aborted by step verification")
      }

      // TODO(rz): Add retry semantics into here as well
      try {
        val startedAt = Instant.now()
        val stepLog = mutableListOf<StepLog>()
        val output = try {
          log.info("${state.id}: Running step ${step.name}")
          step.run(stepLog, stepInputs)
        } catch (e: Exception) {
          // TODO(rz): CheckpointStep should have an interface for handling errors to determine if the step is truly
          // terminal, should be retried, skipped, etc... (basically a verify-like interface)
          log.error("${state.id}: Failed running step ${step.name}", e)
          StepOutput(
            status = StepStatus.TERMINAL,
            description = "Internal step error",
            log = stepLog.also {
              it.add(StepLog(
                label = "Internal error",
                userNotes = "Experienced an internal error while processing step: ${step.name}",
                operatorNotes = "Internal error in ${step.name}: ${e.message}",
                cause = e
              ))
            },
            startedAt = startedAt,
            completedAt = Instant.now()
          )
        }
        state.steps.add(output)
        log.debug("${state.id}: Completed step ${step.name}")

        repository.commit(state)
        log.trace("${state.id}: Committed step ${step.name}")
      } catch (e: Exception) {
        TODO()
      }

      registry.counter(stepInvocationsId).increment()
      log.trace("${state.id}: Completed")
    }
  }
}
