/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.federation;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.federation.location.InstanceLocationProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FederatingOrchestrationProcessor implements OrchestrationProcessor {

  private final static Logger log = LoggerFactory.getLogger(FederatingOrchestrationProcessor.class);

  private final OperationLocationResolver locationResolver;
  private final InstanceLocationProvider locationsProvider;
  private final OrchestrationProcessor localProcessor;

  public FederatingOrchestrationProcessor(OperationLocationResolver locationResolver,
                                          InstanceLocationProvider locationsProvider,
                                          OrchestrationProcessor localProcessor) {
    this.locationResolver = locationResolver;
    this.locationsProvider = locationsProvider;
    this.localProcessor = localProcessor;
  }

  @Override
  public Task process(List<AtomicOperation> atomicOperations, String key) {
    Set<String> locations = atomicOperations.stream()
      .map(locationResolver::resolveLocation)
      .collect(Collectors.toSet());

    if (locations.size() > 1) {
      // TODO(rz): Need a specific exception for this
      // Since we're building all of this for learning-mode and all that jazz, we don't want to blow things up yet
      log.warn("Grouped atomic operations must all resolve to the same shard locations");
    }

//    Set<String> supportedLocations = locationsProvider.provide();
//    if (supportedLocations.containsAll(locations)) {
//      return localProcessor.process(atomicOperations, key);
//    }

    // TODO(rz): Route to the correct place. When we do this, do we want to return to the caller the correct
    // callback to poll for status updates rather than always proxying the requests? Probably definitely.
    // Although if we add that context into the task, then the client will need to know about what region to go to.

    log.info("Received atomic operation requests for unowned shard: {}", locations);
    return localProcessor.process(atomicOperations, key);
  }
}
