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
package com.netflix.spinnaker.cats.dynomite;

import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.spinnaker.cats.redis.RedisClientDelegate;
import redis.clients.jedis.BinaryJedisCommands;
import redis.clients.jedis.JedisCommands;
import redis.clients.jedis.MultiKeyCommands;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.commands.RedisPipeline;

import java.util.function.Consumer;
import java.util.function.Function;

public class DynomiteClientDelegate implements RedisClientDelegate {

  private final DynoJedisClient client;

  public DynomiteClientDelegate(DynoJedisClient client) {
    this.client = client;
  }

  @Override
  public <R> R withCommandsClient(Function<JedisCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withCommandsClient(Consumer<JedisCommands> f) {
    f.accept(client);
  }

  @Override
  public <R> R withMultiClient(Function<MultiKeyCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withMultiClient(Consumer<MultiKeyCommands> f) {
    f.accept(client);
  }

  @Override
  public <R> R withBinaryClient(Function<BinaryJedisCommands, R> f) {
    return f.apply(client);
  }

  @Override
  public void withBinaryClient(Consumer<BinaryJedisCommands> f) {
    f.accept(client);
  }

  @Override
  public void withPipeline(Consumer<RedisPipeline> f) {
    f.accept(client.pipelined());
  }

  @Override
  public <R> R withPipeline(Function<RedisPipeline, R> f) {
    return f.apply(client.pipelined());
  }

  @Override
  public boolean supportsMultiKeyPipelines() {
    return false;
  }

  @Override
  public void withMultiKeyPipeline(Consumer<Pipeline> f) {
    throw new UnsupportedOperationException("Dynomite does not support multi-key pipelined operations");
  }

  @Override
  public <R> R withMultiKeyPipeline(Function<Pipeline, R> f) {
    throw new UnsupportedOperationException("Dynomite does not support multi-key pipelined operations");
  }
}
