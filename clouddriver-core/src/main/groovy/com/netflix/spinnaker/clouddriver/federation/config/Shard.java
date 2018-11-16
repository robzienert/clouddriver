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

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class Shard implements Comparable<Shard> {
  /**
   * The human-readable name of the shard.
   */
  String name;

  /**
   * Dictates whether or not the shard should be considered active to receive
   * traffic.
   */
  Boolean enabled;

  /**
   * The base URL that the shard can be reached at.
   *
   * TODO(rz): How can we make this compatible with the ServiceSelector style stuff?
   */
  String baseUrl;

  /**
   * The priority ranking of the shard. If more than one shard claims
   * ownership of a particular location/account, the shard with the highest
   * priority wins.
   */
  Integer priority = Integer.MIN_VALUE;

  /**
   * A set of clouddriver accounts that this shard is responsible for.
   *
   * If this value is empty, it will be inferred that this shard will handle
   * all accounts for any locations that it is configured for.
   */
  Set<String> accounts = new HashSet<>();

  /**
   * A set of clouddriver locations (regions, datacenters, etc) this shard is
   * responsible for.
   */
  Set<String> locations = new HashSet<>();

  /**
   * Helper method to determine if a shard supports a location/account pair.
   */
  public boolean supports(String location, String account) {
    return supportsLocation(location) && supportsAccount(account);
  }

  private boolean supportsAccount(String account) {
    if (account == null || accounts.isEmpty()) {
      return true;
    }
    return accounts.contains(account);
  }

  private boolean supportsLocation(String location) {
    if (location == null) {
      return true;
    }
    return locations.contains(location);
  }

  @Override
  public int compareTo(Shard o) {
    if (o.priority > priority) {
      return 1;
    } else if (o.priority < priority) {
      return -1;
    }
    return 0;
  }
}
