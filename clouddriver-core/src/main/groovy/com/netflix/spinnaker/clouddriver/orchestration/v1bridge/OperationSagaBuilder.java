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

import com.google.common.annotations.Beta;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A bridge to the yet-to-be-created V2 Operation orchestration.
 *
 * <p>Allows the underlying implementation to be iterated on while keeping support for V1 Atomic
 * Operations that have been migrated over to use Sagas. In migrating V1 Operations to use Sagas,
 * backwards compatibility will be kept for task repository and similar outwardly-facing APIs.
 *
 * <p>TODO(rz): Expose rollback TODO(rz): Events
 */
@Beta
public interface OperationSagaBuilder {

  /**
   * Set the deadline for the operation saga to complete. If exceeded, the operation will
   * automatically become terminal.
   *
   * @param terminalAfterTime The time after which an operation will timeout and become terminal
   */
  @Nonnull
  OperationSagaBuilder deadline(@Nonnull Duration terminalAfterTime);

  /**
   * Optionally define a custom retry handler for the operation as a whole.
   *
   * @param fn The retry callback. The result of which should return [true] if the operation should
   *     retry and [false] if the operation should go terminal.
   */
  @Nonnull
  OperationSagaBuilder retry(@Nonnull Function<ErrorOperationState, Boolean> fn);

  /**
   * Perform a data query. Query steps have different retry semantics than commands, in that they
   * will re-run if the freshnessTtl has elapsed. Under the covers.
   *
   * <p>These should not contain destructive actions (commands): The behavior of including writes as
   * part of a query is undefined behavior.
   *
   * <p>TODO(rz): Should thee state class expose a Versioned type?
   *
   * @param queryName A human-friendly (operator/end-user) name for the step
   * @param freshnessTtl How long the result of this query should remain valid for
   * @param fn The step logic
   */
  @Nonnull
  <T extends StepResult<Object>> OperationSagaBuilder query(
      @Nonnull String queryName, @Nonnull Duration freshnessTtl, @Nonnull OperationStep<T> fn);

  /**
   * Performs a write action using the operation state as input. Any created resources should be
   * included in the result.
   *
   * <p>Actions performed within a command must be written to support re-entrance.
   *
   * @param commandName A human-friendly (operator/end-user) name for the step
   * @param fn The step logic
   */
  @Nonnull
  <T extends CommandStepResult> OperationSagaBuilder command(
      @Nonnull String commandName, @Nonnull OperationStep<T> fn);

  /**
   * Executes the built Operation Saga.
   *
   * <p>This method is idempotent.
   *
   * @param resultType The type of result that can be expected from the last step
   * @param <T> The resulting data, if any
   */
  @Nullable
  <T> T execute(@Nullable Class<T> resultType);

  /**
   * A small result wrapper.
   *
   * @param <T> The result type
   */
  interface StepResult<T> {
    @Nonnull
    T getResult();

    Exception getError();
  }

  /**
   * Command-specific result wrapper.
   *
   * @param <T>
   */
  interface CommandStepResult<T> extends StepResult<T> {
    boolean getRetryable();
  }

  /** Type alias */
  interface OperationStep<T> extends Function<OperationState, StepResult<T>> {}
}
