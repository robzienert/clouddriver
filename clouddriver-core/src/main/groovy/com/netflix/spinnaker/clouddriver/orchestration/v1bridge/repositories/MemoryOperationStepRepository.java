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
package com.netflix.spinnaker.clouddriver.orchestration.v1bridge.repositories;

import com.netflix.spinnaker.clouddriver.orchestration.v1bridge.OperationSagaBuilder;
import com.netflix.spinnaker.clouddriver.orchestration.v1bridge.OperationState;
import com.netflix.spinnaker.clouddriver.orchestration.v1bridge.OperationStepRepository;
import com.netflix.spinnaker.clouddriver.orchestration.v1bridge.utils.Pair;
import de.huxhorn.sulky.ulid.ULID;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IMPORTANT: this code is dreadful. I don't even know why I did it like this, I'm rewriting it to
 * be not bad
 */
public class MemoryOperationStepRepository implements OperationStepRepository {

  private static final Logger log = LoggerFactory.getLogger(MemoryOperationStepRepository.class);
  private static final ULID ID_GENERATOR = new ULID();

  private final SortedMap<ULID.Value, Versions> operations = new TreeMap<>();

  public MemoryOperationStepRepository() {}

  @Override
  public OperationState getOperationState(@Nonnull ULID.Value operationId) {
    if (!operations.containsKey(operationId)) {
      ensureStateRecord(operationId, null);
    }
    Versions versions = operations.get(operationId);
    if (versions.isEmpty()) {
      ULID.Value initialVersion = ID_GENERATOR.nextValue();
      versions.put(initialVersion, new State(operationId, initialVersion, new HashMap<>()));
    }
    return versions.get(versions.lastKey());
  }

  @Override
  public OperationState getOperationState(@Nonnull ULID.Value operationId, ULID.Value version) {
    return ensureStateSnapshot(operationId, operations.get(operationId), version);
  }

  @Nonnull
  @Override
  public OperationState upsertOperationState(@Nonnull OperationState operationState) {
    OperationState old =
        operations.get(operationState.getId()).put(operationState.getId(), operationState);
    if (old == null) {
      return operationState;
    }
    return old;
  }

  @Nonnull
  private Pair<OperationState, List<ULID.Value>> ensureStateRecord(
      @Nonnull ULID.Value operationId, ULID.Value version) {

    // lol
    Versions versions = operations.get(operationId);
    if (versions == null) {
      versions = new Versions();
      operations.put(operationId, versions);
      log.trace("Added Operation: {}", operationId);
      versions.put(operationId, new State(operationId, operationId, new HashMap<>()));
    } else if (!versions.containsKey(version)) {
      versions.put(version, new State(operationId, version, new HashMap<>()));
    }

    return new Pair<>(
        (versions.isEmpty())
            ? null
            : versions.get((version == null) ? versions.lastKey() : version),
        versions.keySet().stream().sorted().collect(Collectors.toList()));
  }

  @Nonnull
  private OperationState ensureStateSnapshot(
      @Nonnull ULID.Value operationId, @Nonnull Versions versions, ULID.Value version) {
    OperationState existing = versions.get(version);
    if (existing != null) {
      return existing;
    }
    // TODO(rz): retry on gen
    State snapshot;
    if (versions.isEmpty()) {
      snapshot = new State(operationId, operationId, new HashMap<>());
    } else if (version == null) {
      snapshot =
          new State(
              operationId, ID_GENERATOR.nextMonotonicValue(versions.lastKey()), new HashMap<>());
    } else if (!versions.containsKey(version)) {
      snapshot = new State(operationId, ID_GENERATOR.nextMonotonicValue(version), new HashMap<>());
    } else {
      throw new IllegalStateException("oops");
    }
    versions.put(snapshot.version, snapshot);
    return snapshot;
  }

  private static class State implements OperationState {

    @Nonnull private final ULID.Value id;
    @Nonnull private final ULID.Value version;
    @Nonnull private final Map<Class<?>, ?> store;
    private final OperationSagaBuilder.StepResult<?> stepResult;

    public State(ULID.Value id, ULID.Value version, Map<Class<?>, ?> store) {
      this(id, version, store, null);
    }

    public State(
        ULID.Value id,
        ULID.Value version,
        Map<Class<?>, ?> store,
        OperationSagaBuilder.StepResult<?> stepResult) {
      this.id = id;
      this.version = version;
      this.store = store;
      this.stepResult = stepResult;
    }

    @Nonnull
    @Override
    public ULID.Value getId() {
      return id;
    }

    @Nonnull
    @Override
    public ULID.Value getVersion() {
      return version;
    }

    @Nullable
    @Override
    public <T> T get(@Nonnull Class<T> stateType) {
      return (T) store.get(stateType);
    }

    @Nullable
    @Override
    public OperationSagaBuilder.StepResult<?> getStepResult() {
      return stepResult;
    }

    @Nonnull
    @Override
    public Pair<OperationState, OperationState> merge(
        OperationSagaBuilder.StepResult<?> stepResult) {
      return new Pair<>(
          new State(this.id, ID_GENERATOR.nextValue(), new HashMap<>(this.store), stepResult),
          this);
    }

    @Override
    public int compareTo(@Nonnull Object o) {
      return ((State) o).version.compareTo(this.version);
    }
  }

  private static class Versions extends TreeMap<ULID.Value, OperationState> {}
}
