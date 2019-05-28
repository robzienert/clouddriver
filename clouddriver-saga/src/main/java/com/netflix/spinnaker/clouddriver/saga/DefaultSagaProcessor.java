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
package com.netflix.spinnaker.clouddriver.saga;

import static java.lang.String.format;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStatus;
import com.netflix.spinnaker.clouddriver.saga.model.SagaStep;
import com.netflix.spinnaker.clouddriver.saga.repository.SagaRepository;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.kork.lock.RefreshableLockManager;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

/**
 * TODO(rz): Instrumentation
 *
 * <p>TODO(rz): Add listeners / adapters (first one implemented would be for getting things setup
 * with Tasks and thread local?
 */
@Slf4j
public class DefaultSagaProcessor implements SagaProcessor {

  private final SagaRepository sagaRepository;
  private final Registry registry;
  private final Optional<RefreshableLockManager> lockManager;

  public DefaultSagaProcessor(
      SagaRepository sagaRepository,
      Registry registry,
      Optional<RefreshableLockManager> lockManager) {
    this.sagaRepository = sagaRepository;
    this.registry = registry;
    this.lockManager = lockManager;
  }

  @Override
  @Nullable
  public <T> T process(Saga saga, Function<SagaState, T> callbackFunction) {
    if (lockManager.isPresent()) {
      //      LockManager.LockOptions lockOptions =
      //        new LockManager.LockOptions()
      //          .withLockName(format("saga:%s", saga.getId()))
      //          .withMaximumLockDuration(Duration.ofMinutes(10));
      //
      //      LockManager.AcquireLockResponse<T> response =
      //        lockManager.get().acquireLock(lockOptions, () -> processInternal(saga,
      // callbackFunction));
      //
      //      return response.getOnLockAcquiredCallbackResult();
      throw new UnsupportedOperationException(
          "Distributed saga resume capabilities are not ready yet");
    } else {
      return processInternal(saga, callbackFunction);
    }
  }

  /**
   * This method is a disaster... but it also covers the basic functionality that needs to ship.
   *
   * <p>TODO(rz): Un-bad this.
   */
  @Nullable
  private <T> T processInternal(Saga inputSaga, Function<SagaState, T> callbackFunction) {
    Saga saga = initializeSaga(inputSaga);
    SagaState state = saga.getLatestState();
    for (SagaStep step : saga.getSteps()) {
      if (shouldSkipStep(saga, step, state)) {
        continue;
      }

      if (shouldFailSaga(saga, step, state)) {
        // TODO(rz): error result type, upsert
        return null;
      }

      final StepResult stepResult;
      try {
        if (!step.getStates().contains(state)) {
          step.getStates().add(state);
        }

        // TODO(rz): Errors; check for retryable. Check for ErrorStepResult
        stepResult = step.getFn().apply(state);
      } catch (Exception e) {
        // TODO(rz): Make less bad
        throw new IntegrationException("Failed applying operation step", e);
      }

      try {
        // TODO(rz): probably should do something with getRight?
        state = state.merge(stepResult).getLeft();

        step.getStates().add(state);

        sagaRepository.upsert(step);
      } catch (Exception e) {
        throw new SystemException("Failed updating operation state", e);
      }
    }

    try {
      return callbackFunction.apply(state);
    } catch (Exception e) {
      // TODO(rz): Should cause the saga to go terminal_fatal
      throw new IntegrationException(
          "OperationSagaProcessor callback function failed to produce a result", e);
    }
  }

  private Saga initializeSaga(Saga saga) {
    Saga storedSaga = sagaRepository.get(saga.getId());
    if (storedSaga == null) {
      return sagaRepository.upsert(saga);
    }

    // Perform a checksum on the originally stored inputs versus the inputs provided from the most
    // recent execution. If these are not the same, we will abort this specific request.
    if (saga.getChecksum().equals(storedSaga.getChecksum())) {
      throw new InputsChecksumMismatchException(saga.getChecksum(), storedSaga.getChecksum());
    }

    // If we're picking up a saga where the latest state does not have a RUNNING status set it to
    // RUNNING and increment the attempts.
    if (saga.getStatus() != SagaStatus.RUNNING) {
      saga.restart();
      return sagaRepository.upsert(saga);
    }

    return saga;
  }

  private boolean shouldSkipStep(Saga saga, SagaStep step, @Nullable SagaState state) {
    if (step.getStatus() == SagaStatus.SUCCEEDED) {
      return true;
    }

    // TODO(rz): Logic surrounding query ttl
    return false;
  }

  private boolean shouldFailSaga(Saga saga, SagaStep step, @Nullable SagaState state) {
    // TODO(rz): Should roll-up the latest step status to the saga so we can check that instead
    if (step.getStatus() == SagaStatus.TERMINAL_FATAL) {
      return true;
    }
    return false;
  }

  static class InputsChecksumMismatchException extends UserException {
    InputsChecksumMismatchException(String storedChecksum, String providedChecksum) {
      super(
          format(
              "Provided inputs checksum (%s) does not match original stored checksum (%s)",
              storedChecksum, providedChecksum));
    }
  }
}
