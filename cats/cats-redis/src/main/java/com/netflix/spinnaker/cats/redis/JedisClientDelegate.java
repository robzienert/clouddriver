/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.cats.redis;

import redis.clients.jedis.BinaryJedisCommands;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.MultiKeyCommands;

import java.util.function.Consumer;
import java.util.function.Function;

public class JedisClientDelegate implements RedisClientDelegate {

  JedisSource jedisSource;


  @Override
  public JedisCommands getCommandsClient() {
    return jedisSource.getJedis();
  }

  @Override
  public <R> R withCommandsClient(Function<JedisCommands, R> f) {
    try (Jedis jedis = jedisSource.getJedis()) {
      return f.apply(jedis);
    }
  }

  @Override
  public void withCommandsClient(Consumer<JedisCommands> f) {
    try (Jedis jedis = jedisSource.getJedis()) {
      f.accept(jedis);
    }
  }

  @Override
  public MultiKeyCommands getMultiClient() {
    return jedisSource.getJedis();
  }

  @Override
  public <R> R withMultiClient(Function<MultiKeyCommands, R> f) {
    try (Jedis jedis = jedisSource.getJedis()) {
      return f.apply(jedis);
    }
  }

  @Override
  public void withMultiClient(Consumer<MultiKeyCommands> f) {
    try (Jedis jedis = jedisSource.getJedis()) {
      f.accept(jedis);
    }
  }

  @Override
  public BinaryJedisCommands getBinaryClient() {
    return jedisSource.getJedis();
  }

  @Override
  public <R> R withBinaryClient(Function<BinaryJedisCommands, R> f) {
    try (Jedis jedis = jedisSource.getJedis()) {
      return f.apply(jedis);
    }
  }

  @Override
  public void withBinaryClient(Consumer<BinaryJedisCommands> f) {
    try (Jedis jedis = jedisSource.getJedis()) {
      f.accept(jedis);
    }
  }
}
