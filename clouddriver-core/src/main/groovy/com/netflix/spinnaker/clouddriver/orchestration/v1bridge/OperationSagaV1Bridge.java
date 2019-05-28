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
package com.netflix.spinnaker.clouddriver.orchestration.v1bridge;

import com.netflix.spinnaker.clouddriver.orchestration.v1bridge.repositories.MemoryOperationStepRepository;
import de.huxhorn.sulky.ulid.ULID;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class OperationSagaV1Bridge implements OperationSagaBuilder {

  @Nonnull private final ULID.Value uuid;

  @Nonnull private final OperationStepRepository stepRepository;

  private Function<ErrorOperationState, Boolean> retry;
  private Duration deadline;
  private Duration queryFreshnessTtl;

  private List<OperationStep<?>> steps = new ArrayList<>();
  private Map<String, Duration> queryFreshnessTtls = new HashMap<>();

  public OperationSagaV1Bridge(@Nonnull ULID.Value uuid) {
    this.uuid = uuid;
    this.stepRepository = new MemoryOperationStepRepository(); // TODO(rz): ok
  }

  @Nonnull
  @Override
  public OperationSagaBuilder deadline(@Nonnull Duration terminalAfterTime) {
    deadline = terminalAfterTime;
    return this;
  }

  @Nonnull
  @Override
  public OperationSagaBuilder retry(@Nonnull Function<ErrorOperationState, Boolean> fn) {
    retry = fn;
    return this;
  }

  @Nonnull
  @Override
  public <T extends StepResult<Object>> OperationSagaBuilder query(
      @Nonnull String queryName, @Nonnull Duration freshnessTtl, @Nonnull OperationStep<T> fn) {
    steps.add(fn);
    queryFreshnessTtls.put(queryName, freshnessTtl);
    return this;
  }

  @Nonnull
  @Override
  public <T extends CommandStepResult> OperationSagaBuilder command(
      @Nonnull String commandName, @Nonnull OperationStep<T> fn) {
    steps.add(fn);
    return this;
  }

  @Nullable
  @Override
  public <T> T execute(@Nullable Class<T> resultType) {
    // TODO(rz): The builder should pass everything off to a processor instead

    StepResult<?> lastStepResult = null;
    OperationState state = stepRepository.getOperationState(uuid);
    for (OperationStep<?> step : steps) {

      if (shouldSkipStep(step, state)) {
        continue;
      }
      if (shouldFailOperation(step, state)) {
        // TODO(rz): error result type, upsertOperationState
        return null;
      }

      try {
        StepResult<?> stepResult = step.apply(state);

        try {
          state = stepRepository.upsertOperationState(state.merge(stepResult).getA());
        } finally {
        }
        lastStepResult = stepResult;
      } finally {
      }
    }

    if (lastStepResult != null) {
      return (T) lastStepResult.getResult();
    }
    return null;
  }

  private boolean shouldSkipStep(OperationStep<?> operationStep, OperationState operationState) {
    return false;
  }

  private boolean shouldFailOperation(
      OperationStep<?> operationStep, OperationState operationState) {
    return false;
  }
}
