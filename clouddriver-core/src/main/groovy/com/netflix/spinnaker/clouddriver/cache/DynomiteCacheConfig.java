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
package com.netflix.spinnaker.clouddriver.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.dyno.connectionpool.ConnectionPoolConfiguration.LoadBalancingStrategy;
import com.netflix.dyno.connectionpool.Host;
import com.netflix.dyno.connectionpool.HostSupplier;
import com.netflix.dyno.connectionpool.TokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.ConnectionPoolConfigurationImpl;
import com.netflix.dyno.connectionpool.impl.lb.AbstractTokenMapSupplier;
import com.netflix.dyno.connectionpool.impl.lb.HostToken;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.cache.NamedCacheFactory;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.dynomite.cache.DynomiteNamedCacheFactory;
import com.netflix.spinnaker.cats.redis.RedisClientDelegate;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache.CacheMetrics;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache.CacheMetrics.NOOP;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeStatusProvider;
import com.netflix.spinnaker.cats.redis.cluster.NodeStatusProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Configuration
@ConditionalOnExpression("${dynomite.enabled:false}")
@EnableConfigurationProperties(DynomiteConfigurationProperties.class)
public class DynomiteCacheConfig {

  @Bean
  @ConditionalOnBean(DiscoveryClient.class)
  DynoJedisClient dynoJedisClient(DiscoveryClient discoveryClient, DynomiteConfigurationProperties dynomiteConfigurationProperties) {
    return new DynoJedisClient.Builder()
      .withApplicationName(dynomiteConfigurationProperties.getApplicationName())
      .withDynomiteClusterName(dynomiteConfigurationProperties.getClusterName())
      .withDiscoveryClient(discoveryClient)
      .build();
  }

  @Bean
//  @ConditionalOnMissingBean(DiscoveryClient.class)
  DynoJedisClient dynoJedisClient(DynomiteConfigurationProperties dynomiteConfigurationProperties) {
    return new DynoJedisClient.Builder()
      .withApplicationName(dynomiteConfigurationProperties.getApplicationName())
      .withDynomiteClusterName(dynomiteConfigurationProperties.getClusterName())
      .withHostSupplier(new StaticHostSupplier(dynomiteConfigurationProperties.getDynoHosts()))
      .withCPConfig(
        // TODO rz - Add support for TokenAware LB (need token map supplier)
        new ConnectionPoolConfigurationImpl(dynomiteConfigurationProperties.getApplicationName())
          .withTokenSupplier(new StaticTokenMapSupplier(dynomiteConfigurationProperties.getDynoHostTokens()))
          .setLoadBalancingStrategy(LoadBalancingStrategy.TokenAware)
          .setLocalDataCenter(dynomiteConfigurationProperties.getLocalDataCenter())
          .setLocalRack(dynomiteConfigurationProperties.getLocalRack())
      )
      .build();
  }

  @Bean
  RedisClientDelegate dynomiteClientDelegate(DynoJedisClient dynoJedisClient) {
    return new DynomiteClientDelegate(dynoJedisClient);
  }

  @Bean
  CacheMetrics cacheMetrics(Registry registry) {
    // TODO rz - concrete impl
    return new NOOP();
  }

  @Bean
  NamedCacheFactory cacheFactory(
    RedisClientDelegate dynomiteClientDelegate,
    ObjectMapper objectMapper,
    RedisCacheOptions redisCacheOptions,
    CacheMetrics cacheMetrics) {
    return new DynomiteNamedCacheFactory(dynomiteClientDelegate, objectMapper, redisCacheOptions, cacheMetrics);
  }

  @Bean
  @ConditionalOnBean(DiscoveryClient.class)
  EurekaStatusNodeStatusProvider discoveryStatusNodeStatusProvider(DiscoveryClient discoveryClient) {
    return new EurekaStatusNodeStatusProvider(discoveryClient);
  }

  @Bean
  @ConditionalOnMissingBean(NodeStatusProvider.class)
  DefaultNodeStatusProvider nodeStatusProvider() {
    return new DefaultNodeStatusProvider();
  }

  static class StaticHostSupplier implements HostSupplier {

    private final List<Host> hosts;

    public StaticHostSupplier(List<Host> hosts) {
      this.hosts = hosts;
    }

    @Override
    public Collection<Host> getHosts() {
      return hosts;
    }
  }

  static class StaticTokenMapSupplier implements TokenMapSupplier {

    List<HostToken> hostTokens = new ArrayList<>();

    public StaticTokenMapSupplier(List<HostToken> hostTokens) {
      this.hostTokens = hostTokens;
    }

    @Override
    public List<HostToken> getTokens(Set<Host> activeHosts) {
      return hostTokens;
    }

    @Override
    public HostToken getTokenForHost(Host host, Set<Host> activeHosts) {
      return hostTokens.stream().filter(t -> t.getHost() == host).findFirst().get();
    }
  }
}
