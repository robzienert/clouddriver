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
package com.netflix.spinnaker.clouddriver.aws.deploy.description;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.clouddriver.deploy.servergroup.ResizeStrategy;
import com.netflix.spinnaker.clouddriver.model.ServerGroup;
import com.netflix.spinnaker.clouddriver.security.resources.ServerGroupNameable;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import javax.annotation.Nullable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@NonnullByDefault
public class ResizeServerGroupDescription extends AbstractAmazonCredentialsDescription
    implements ServerGroupNameable,
        com.netflix.spinnaker.clouddriver.deploy.servergroup.ResizeServerGroupDescription {

  /** Desired capacity. */
  private ServerGroup.Capacity capacity = new ServerGroup.Capacity();

  /** Required initial capacity before taking action. */
  @Nullable private ServerGroup.Capacity constraints = null;

  /** The server group name. */
  private String serverGroupName;

  /** The location (region) of the server group. */
  private String location;

  /** TODO(rz): Oddly named... probably not worth going 1-for-1 on Orca migration here. */
  private ResizeStrategy.ResizeAction strategy;

  @JsonIgnore
  @Override
  public ResizeStrategy.ResizeCapacityCommand toResizeCapacityCommand() {
    return new ResizeStrategy.ResizeCapacityCommand(
        capacity,
        getCredentials().getCloudProvider(),
        getCredentials().getName(),
        location,
        serverGroupName);
  }
}
