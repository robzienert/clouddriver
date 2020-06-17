/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.clouddriver.deploy.servergroup;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Collection;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Provides helper functions for {@link ResizeStrategy} implementations. */
@Component
@NonnullByDefault
public class ResizeStrategySupport {

  private static final Logger log = LoggerFactory.getLogger(ResizeStrategySupport.class);

  private final Collection<ClusterProvider> clusterProviders;
  private final Registry registry;

  public ResizeStrategySupport(Collection<ClusterProvider> clusterProviders, Registry registry) {
    this.clusterProviders = clusterProviders;
    this.registry = registry;
  }

  /** Get the {@link ClusterProvider} for the given cloud provider, if one exists. */
  public Optional<ClusterProvider> clusterProviderForCloud(String cloudProvider) {
    return clusterProviders.stream()
        .filter(it -> it.getCloudProviderId().equals(cloudProvider))
        .findFirst();
  }

  public ServerGroup.Capacity performScalingAndPinning(
      ServerGroup.Capacity sourceCapacity, ResizeStrategy.ResizeCapacityCommand command) {
    ServerGroup.Capacity newCapacity = sourceCapacity;
    if (command.getScalePct() != null) {
      double factor = command.getScalePct() / 100.0d;
      newCapacity = scalePct(sourceCapacity, factor);
    }

    if (command.isUnpinMinimumCapacity()) {
      ServerGroup.Capacity originalSourceCapacity = command.getSource().getCapacity();
      Integer originalMin =
          Optional.ofNullable(originalSourceCapacity)
              .map(ServerGroup.Capacity::getMin)
              .orElse(null);

      if (originalMin != null) {
        newCapacity = unpinMin(newCapacity, originalMin);
      } else {
        // TODO(rz): Log information about what command this came from, etc.
        log.warn(
            "Resize stage has unpinMinimumCapacity==true but could not find the original minimum value");
        registry.counter("operations.resizeServerGroup.failedUnpinMin").increment();
      }
    }

    if (command.isPinCapacity()) {
      return pinCapacity(newCapacity);
    }

    return command.isPinMinimumCapacity() ? pinMin(newCapacity) : newCapacity;
  }

  private static ServerGroup.Capacity scalePct(ServerGroup.Capacity capacity, double factor) {
    int newDesired = (int) Math.ceil(capacity.getDesired() * factor);
    return new ServerGroup.Capacity(
        Math.min(
            capacity.getMin(), newDesired), // in case scalePct pushed desired below current min
        Math.max(
            capacity.getMax(), newDesired), // in case scalePct pushed desired above current max
        newDesired);
  }

  private static ServerGroup.Capacity pinMin(ServerGroup.Capacity newCapacity) {
    return new ServerGroup.Capacity(
        newCapacity.getDesired(), newCapacity.getMax(), newCapacity.getDesired());
  }

  private static ServerGroup.Capacity unpinMin(
      ServerGroup.Capacity newCapacity, Integer originalMin) {
    if (originalMin > newCapacity.getMin()) {
      log.warn(
          "Cannot unpin the minimum of {} to a higher originalMin={}", newCapacity, originalMin);
      return newCapacity;
    }
    return new ServerGroup.Capacity(originalMin, newCapacity.getMax(), newCapacity.getDesired());
  }

  private static ServerGroup.Capacity pinCapacity(ServerGroup.Capacity newCapacity) {
    return new ServerGroup.Capacity(
        newCapacity.getDesired(), newCapacity.getDesired(), newCapacity.getDesired());
  }
}
