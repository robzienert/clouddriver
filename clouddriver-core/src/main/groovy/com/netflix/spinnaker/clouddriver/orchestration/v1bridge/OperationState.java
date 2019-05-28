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

import com.netflix.spinnaker.clouddriver.orchestration.v1bridge.utils.Pair;
import de.huxhorn.sulky.ulid.ULID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The state for an operation at a given version. */
public interface OperationState extends Comparable {
  @Nonnull
  ULID.Value getId();

  @Nonnull
  ULID.Value getVersion();

  @Nullable
  OperationSagaBuilder.StepResult<?> getStepResult();

  @Nullable
  @SuppressWarnings("unchecked") // TODO(rz): this is so bad
  <T> T get(@Nonnull Class<T> stateType);

  @Nonnull
  Pair<OperationState, OperationState> merge(OperationSagaBuilder.StepResult<?> stepResult);
}
