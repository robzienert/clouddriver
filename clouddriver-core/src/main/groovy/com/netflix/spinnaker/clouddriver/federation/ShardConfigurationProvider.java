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

import com.netflix.spinnaker.clouddriver.federation.location.InstanceLocationProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;

import java.util.HashSet;
import java.util.Set;

public class ShardConfigurationProvider {

  private final FederationConfigurationProperties properties;
  private final DynamicConfigService dynamicConfigService;
  private final InstanceLocationProvider instanceLocationProvider;

  public ShardConfigurationProvider(FederationConfigurationProperties properties,
                                    DynamicConfigService dynamicConfigService,
                                    InstanceLocationProvider instanceLocationProvider) {
    this.properties = properties;
    this.dynamicConfigService = dynamicConfigService;
    this.instanceLocationProvider = instanceLocationProvider;
  }

  public boolean supportsOperation(AtomicOperation<?> operation) {
    String location = operation.getLocation();

    // TODO
    Set<String> accounts = new HashSet<>();

    if (instanceLocationProvider.provide().equals(location)) {
      return true;
    }
    return false;
  }

  public FederationConfigurationProperties.ShardProperties shardForOperation(AtomicOperation<?> operation) {
    // loop the configs, return the shard properties that match the operation
    return null;
  }
}
