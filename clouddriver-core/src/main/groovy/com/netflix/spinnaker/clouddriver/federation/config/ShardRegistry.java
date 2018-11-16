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

import javax.annotation.Nonnull;
import java.util.*;

public class ShardRegistry {

  private final Set<Shard> shards = new HashSet<>();

  public Optional<Shard> getShard(@Nonnull String name) {
    return shards.stream().filter(s -> s.name.equals(name)).findFirst();
  }

  public Set<Shard> findShards(String location, String account) {
    throw new UnsupportedOperationException("nope");
  }
}
