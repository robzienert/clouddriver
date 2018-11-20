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
package com.netflix.spinnaker.clouddriver.federation.config;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.federation.FederationConfigurationProperties;
import com.netflix.spinnaker.clouddriver.federation.location.InstanceLocationProvider;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class ShardConfigurationProvider {

  private final static Splitter LIST_SPLITTER = Splitter.on(",");

  private final FederationConfigurationProperties properties;
  private final DynamicConfigService dynamicConfigService;
  private final InstanceLocationProvider instanceLocationProvider;

  private final Map<String, Shard> shards = new ConcurrentHashMap<>();

  public ShardConfigurationProvider(FederationConfigurationProperties properties,
                                    DynamicConfigService dynamicConfigService,
                                    InstanceLocationProvider instanceLocationProvider) {
    this.properties = properties;
    this.dynamicConfigService = dynamicConfigService;
    this.instanceLocationProvider = instanceLocationProvider;

    refreshShardConfiguration();
  }

  void refreshShardConfiguration() {
    properties.getShards().forEach((shardName, shardProperties) -> {
      Boolean enabled = dynamicConfigService.getConfig(Boolean.class, getShardConfigKey("enabled"), true);
      Integer priority = dynamicConfigService.getConfig(Integer.class, getShardConfigKey("priority"), shardProperties.getPriority());
      Set<String> accounts = getConfigSet("accounts", new HashSet<>(shardProperties.getAccounts()));
      Set<String> locations = getConfigSet("locations", new HashSet<>(shardProperties.getLocations()));

      shards.put(
        shardName,
        new Shard()
          .setName(shardName)
          .setEnabled(enabled)
          .setBaseUrl(shardProperties.getBaseUrl())
          .setPriority(priority)
          .setLocations(locations)
          .setAccounts(accounts)
      );
    });
    Set<String> diff = Sets.difference(shards.keySet(), properties.getShards().keySet());
    if (!diff.isEmpty()) {
      diff.forEach(shards::remove);
    }
  }

  public List<Shard> allShards() {
    return Collections.unmodifiableList(new ArrayList<>(shards.values()));
  }

  public List<Shard> findShards(String location, String account) {
    return shards.values()
      .stream()
      .filter(s -> s.supports(location, account))
      .sorted()
      .collect(Collectors.toList());
  }

  public boolean localSupport(String location, String account) {
    Shard local = shards.get(properties.getShardName());
    if (local == null) {
      return false;
    }
    return local.supports(location, account);
  }

  public boolean supportsOperation(AtomicOperation<?> operation) {
    String location = operation.getLocation();

    // TODO
    Set<String> accounts = new HashSet<>();

    // TODO an instance may support more than one location, tho.
    if (instanceLocationProvider.provide().equals(location)) {
      return true;
    }
    return false;
  }

  public FederationConfigurationProperties.ShardProperties shardForOperation(AtomicOperation<?> operation) {
    // loop the configs, return the shard properties that match the operation
    return null;
  }

  private String getShardConfigKey(String key) {
    return format("federation.%s.%s", properties.getShardName(), key);
  }

  private Set<String> getConfigSet(String key, Set<String> defaultValue) {
    return splitToSet(dynamicConfigService.getConfig(String.class, getShardConfigKey(key), ""), defaultValue);
  }

  private Set<String> splitToSet(String input, Set<String> defaultValue) {
    if (input.isEmpty()) {
      return defaultValue;
    }
    return Sets.newHashSet(LIST_SPLITTER.split(input));
  }
}
