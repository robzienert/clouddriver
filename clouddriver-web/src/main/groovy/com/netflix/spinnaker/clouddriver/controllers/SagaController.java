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
package com.netflix.spinnaker.clouddriver.controllers;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.saga.model.Saga;
import com.netflix.spinnaker.clouddriver.saga.model.SagaState;
import com.netflix.spinnaker.clouddriver.saga.repository.ListStateCriteria;
import com.netflix.spinnaker.clouddriver.saga.repository.SagaRepository;
import com.netflix.spinnaker.clouddriver.saga.utils.Pair;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.Optional;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/saga")
@RestController
@Slf4j
public class SagaController {

  private final SagaRepository sagaRepository;

  @Autowired
  public SagaController(SagaRepository sagaRepository) {
    this.sagaRepository = sagaRepository;
  }

  @GetMapping()
  SagaRepository.ListResult<Saga> list() {
    return sagaRepository.list(ListStateCriteria.none());
  }

  @GetMapping("/{id}")
  Saga get(@PathVariable String id) {
    return Optional.ofNullable(sagaRepository.get(id))
        .orElseThrow(() -> new NotFoundException(format("Saga '%s' not found", id)));
  }

  @DeleteMapping
  ReapResponse reap() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @PutMapping("/{id}/status")
  void putStatus(@PathVariable String id, @RequestParam("status") SagaState.Status status) {
    if (status == null) {
      throw new InvalidRequestException("Required request parameter 'status' is missing");
    }

    Pair<SagaState, SagaState> states = getSagaStates(id);
    states.getLeft().setStatus(status);
    sagaRepository.upsert(states.getLeft());
  }

  @PutMapping("/{id}/resume")
  void resume(@PathVariable String id) {
    Pair<SagaState, SagaState> states = getSagaStates(id);
    states.getLeft().setStatus(SagaState.Status.RUNNING);
    sagaRepository.upsert(states.getLeft());
  }

  private Pair<SagaState, SagaState> getSagaStates(@Nonnull String id) {
    Saga saga = sagaRepository.get(id);
    if (saga == null) {
      throw new NotFoundException(format("Saga '%s' not found", id));
    }
    return saga.getLatestState().merge(null);
  }

  static class ReapResponse {
    long deletedCount;
  }
}
