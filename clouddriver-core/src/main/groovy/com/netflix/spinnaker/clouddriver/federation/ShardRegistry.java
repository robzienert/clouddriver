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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ShardRegistry {

  private final Map<String, ShardDimensions> shards = new HashMap<>();

  public ShardDimensions getShard(String name) {
    return shards.get(name);
  }

  public ShardDimensions findShard(String location, Set<String> accounts) {
    throw new UnsupportedOperationException("nope");
  }

  public Map<String, ShardDimensions> findShards(ShardSelectionCriteria criteria) {
    throw new UnsupportedOperationException("nope");
  }

  public static class ShardSelectionCriteria {

    private final Map<String, Set<String>> criteria = new HashMap<>();

    public ShardSelectionCriteria withShard(String location, Set<String> accounts) {
      if (criteria.containsKey(location)) {
        criteria.get(location).addAll(accounts);
      } else {
        criteria.put(location, accounts);
      }
      return this;
    }
  }
}
