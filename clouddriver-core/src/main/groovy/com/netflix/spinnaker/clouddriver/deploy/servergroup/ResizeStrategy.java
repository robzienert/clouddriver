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

import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Collection;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@NonnullByDefault
public interface ResizeStrategy {

  /** Whether or not the strategy handles the given action. */
  boolean handles(ResizeAction action);

  /** Calculate the target server group capacity. */
  CapacitySet capacity(ResizeCapacityCommand command);

  enum ResizeAction {
    /** Scale to the exact capacity provided. */
    SCALE_EXACT,

    /** Scale up by a static number or by a certain percentage. */
    SCALE_UP,

    /** Scale down by a static number or by a certain percentage. */
    SCALE_DOWN,

    /**
     * Scale to match the given cluster's capacity, using an associated server group resolution
     * strategy.
     */
    SCALE_TO_CLUSTER,

    /** Scale to match the a server group's capacity. */
    SCALE_TO_SERVER_GROUP
  }

  @Data
  @Builder
  class ResizeCapacityCommand {
    ResizeAction action;

    ServerGroup.Capacity capacity;

    String cloudProvider;
    String credentials;
    String location;
    String serverGroupName;

    Source source;

    /** Whether or not `min` capacity should be set to `desired` capacity. */
    boolean pinMinimumCapacity;

    /** TODO(rz): Purpose? */
    boolean unpinMinimumCapacity;

    /** TODO(rz): Purpose? */
    boolean pinCapacity;

    /** TODO(rz): Purpose? */
    Integer scalePct;

    /** TODO(rz): Purpose? */
    Integer scaleNum;
  }

  @Data
  @Builder
  class Source {
    // TODO(rz): What is actually used / preferred?
    Collection<String> zones;
    Collection<String> regions;
    String region;
    String zone;

    String serverGroupName;
    String credentials;
    String cloudProvider;

    ServerGroup.Capacity capacity;
  }

  @Value
  class CapacitySet {
    ServerGroup.Capacity original;
    ServerGroup.Capacity target;
  }
}
