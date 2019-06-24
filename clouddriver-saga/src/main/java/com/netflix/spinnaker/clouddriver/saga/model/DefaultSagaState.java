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
package com.netflix.spinnaker.clouddriver.saga.model;

import com.netflix.spinnaker.clouddriver.saga.StepResult;
import com.netflix.spinnaker.clouddriver.saga.utils.Pair;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import com.netflix.spinnaker.kork.exceptions.SystemException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.String.format;

/**
 * TODO(rz): Would prefer to make into a value object...
 *
 * <p>TODO(rz): Need a way to propagate errors as well. Probably another top-level prop and remove
 * StepResult.
 */
public class DefaultSagaState implements SagaState {

  @Nonnull private final Instant version;
  @Nonnull private final Map<String, Object> persistedStore;
  @Nonnull private final Map<String, Object> runtimeStore;
  @Nonnull private final List<String> logs = new ArrayList<>();
  @Nonnull private SagaStatus status;
  @Nullable private Error error;

  public DefaultSagaState(@Nonnull Map<String, Object> inputs) {
    this(Instant.now(), SagaStatus.RUNNING, new HashMap<>(inputs), new HashMap<>());
  }

  public DefaultSagaState(
      @Nonnull SagaStatus status,
      @Nonnull Map<String, Object> persistedStore,
      @Nonnull Map<String, Object> runtimeStore) {
    this(Instant.now(), status, persistedStore, runtimeStore);
  }

  public DefaultSagaState(
      @Nonnull Instant version,
      @Nonnull SagaStatus status,
      @Nonnull Map<String, Object> persistedStore,
      @Nonnull Map<String, Object> runtimeStore) {
    this.version = version;
    this.status = status;
    this.persistedStore = persistedStore;
    this.runtimeStore = runtimeStore;
  }

  @Nonnull
  @Override
  public Instant getVersion() {
    return version;
  }

  @Nonnull
  @Override
  public SagaStatus getStatus() {
    return status;
  }

  @Override
  public void setStatus(@Nonnull SagaStatus status) {
    this.status = status;
  }

  @Nullable
  @Override
  public Error getError() {
    return error;
  }

  @Override
  public void setError(@Nonnull Error error) {
    this.error = error;
  }

  @Nonnull
  @Override
  public Map<String, Object> getPersistedState() {
    return persistedStore;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T get(@Nonnull String key, @Nonnull Class<T> stateType) {
    // TODO(rz): Use getInternal
    Object o = persistedStore.get(key);
    if (o == null) {
      return null;
    }
    try {
      return (T) o;
    } catch (ClassCastException e) {
      throw new DesiredTypeMismatchException(
          format(
              "State value for '%s' is of type '%s' but '%s' was requested",
              key, o.getClass().getSimpleName(), stateType.getSimpleName()),
          e);
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T get(@Nonnull String key) {
    return (T)
        Optional.ofNullable(getInternal(Backend.RUNTIME, key))
            .orElse(getInternal(Backend.PERSISTED, key));
  }

  @Nonnull
  @Override
  public <T> T getRequired(@Nonnull String key) {
    T v = get(key);
    if (v == null) {
      throw new RequiredStateKeyNotFoundException(key);
    }
    return v;
  }

  @Nonnull
  @Override
  public <T> T getRequired(@Nonnull String key, @Nonnull Class<T> stateType) {
    T v = get(key, stateType);
    if (v == null) {
      throw new RequiredStateKeyNotFoundException(key);
    }
    return v;
  }

  @SuppressWarnings("unchecked")
  private <T> T getInternal(@Nonnull Backend backend, @Nonnull String key) {
    Object o = ((backend == Backend.RUNTIME) ? runtimeStore : persistedStore).get(key);
    if (o == null) {
      return null;
    }
    try {
      return (T) o;
    } catch (ClassCastException e) {
      throw new DesiredTypeMismatchException(
          format("State value for '%s' in '%s' does not match expected type", key, backend), e);
    }
  }

  @Override
  public void put(@Nonnull String key, @Nullable Object object) {
    runtimeStore.put(key, object);
  }

  @Override
  public void appendLog(@Nonnull String message) {
    this.logs.add(message);
  }

  @Override
  public List<String> getLogs() {
    return new ArrayList<>(logs);
  }

  @Nonnull
  @Override
  public Pair<SagaState, SagaState> merge(@Nullable StepResult stepResult) {
    Map<String, Object> persistedStore = new HashMap<>(this.persistedStore);
    if (stepResult != null) {
      persistedStore.putAll(stepResult.getResults());
    }
    Map<String, Object> runtimeStore = new HashMap<>(this.runtimeStore);

    return new Pair<>(new DefaultSagaState(this.status, persistedStore, runtimeStore), this);
  }

  @Override
  public int compareTo(@Nonnull Object o) {
    return ((DefaultSagaState) o).version.compareTo(this.version);
  }

  private enum Backend {
    PERSISTED,
    RUNTIME
  }

  public static class DesiredTypeMismatchException extends SystemException {
    DesiredTypeMismatchException(String message, Throwable cause) {
      super(message, cause);
      this.setRetryable(false);
    }
  }

  public static class RequiredStateKeyNotFoundException extends IntegrationException {
    public RequiredStateKeyNotFoundException(String missingKey) {
      super(format("Required state '%s' is not present", missingKey));
    }
  }
}
