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

import de.huxhorn.sulky.ulid.ULID;
import javax.annotation.Nonnull;

public interface OperationStepRepository {
  //    @Nonnull Pair<List<OperationState>, Long> getOperations();
  //    @Nonnull Pair<List<OperationState>, Long> getOperations(String token);
  //    @Nonnull Pair<List<OperationState>, Long> getOperations(@Nonnull Duration age);

  OperationState getOperationState(@Nonnull ULID.Value operationId);

  OperationState getOperationState(@Nonnull ULID.Value operationId, ULID.Value version);

  @Nonnull
  OperationState upsertOperationState(@Nonnull OperationState operationState);

  //    long reap(long maxRecords);
  //    long reap(Instant olderThan);
}
