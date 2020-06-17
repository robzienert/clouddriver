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

import static com.netflix.spinnaker.clouddriver.deploy.servergroup.ResizeStrategy.ResizeAction.SCALE_EXACT;
import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ScaleExactResizeStrategy implements ResizeStrategy {

  private final ResizeStrategySupport resizeStrategySupport;

  public ScaleExactResizeStrategy(ResizeStrategySupport resizeStrategySupport) {
    this.resizeStrategySupport = resizeStrategySupport;
  }

  @Override
  public boolean handles(ResizeAction action) {
    return SCALE_EXACT.equals(action);
  }

  @Override
  public ResizeStrategy.CapacitySet capacity(ResizeStrategy.ResizeCapacityCommand command) {
    ServerGroup.Capacity currentCapacity = getCurrentCapacity(command);
    ServerGroup.Capacity targetCapacity = command.getCapacity();
    ServerGroup.Capacity mergedCapacity =
        mergeConfiguredCapacityWithCurrent(targetCapacity, currentCapacity);

    return new ResizeStrategy.CapacitySet(
        currentCapacity, resizeStrategySupport.performScalingAndPinning(mergedCapacity, command));
  }

  private static ServerGroup.Capacity mergeConfiguredCapacityWithCurrent(
      ServerGroup.Capacity configured, ServerGroup.Capacity current) {
    boolean minConfigured = configured.getMin() != null;
    boolean desiredConfigured = configured.getDesired() != null;
    boolean maxConfigured = configured.getMax() != null;

    ServerGroup.Capacity result = new ServerGroup.Capacity();
    result.setMin(minConfigured ? configured.getMin() : current.getMin());

    if (maxConfigured) {
      result.setMax(configured.getMax());
      result.setMin(Math.min(result.getMin(), configured.getMax()));
    } else {
      result.setMax(Math.max(result.getMin(), orZero(current.getMax())));
    }

    if (desiredConfigured) {
      result.setDesired(configured.getDesired());
    } else {
      result.setDesired(current.getDesired());
      if (current.getDesired() < result.getMin()) {
        result.setDesired(result.getMin());
      }
      if (current.getDesired() > result.getMax()) {
        result.setDesired(result.getMax());
      }
    }

    return result;
  }

  private ServerGroup.Capacity getCurrentCapacity(ResizeStrategy.ResizeCapacityCommand command) {
    return resizeStrategySupport
        .clusterProviderForCloud(command.getCloudProvider())
        .map(
            it ->
                it.getServerGroup(
                    command.getCredentials(), command.getLocation(), command.getServerGroupName()))
        .map(ServerGroup::getCapacity)
        .orElseThrow(
            () ->
                new IntegrationException(
                    format(
                        "Could not find ClusterProvider for cloud provider '%s'",
                        command.getCloudProvider())));
  }

  private static int orZero(Integer value) {
    return Optional.ofNullable(value).orElse(0);
  }
}
