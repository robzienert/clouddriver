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
package com.netflix.spinnaker.cats.dynomite.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.netflix.dyno.jedis.DynoJedisClient;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.dynomite.DynomiteClientDelegate;
import com.netflix.spinnaker.cats.redis.cache.AbstractRedisCache;
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public class DynomiteCache extends AbstractRedisCache {

  public DynomiteCache(String prefix, DynomiteClientDelegate dynomiteClientDelegate, ObjectMapper objectMapper, RedisCacheOptions options, CacheMetrics cacheMetrics) {
    super(prefix, dynomiteClientDelegate, objectMapper, options, cacheMetrics);
  }

  @Override
  public void mergeItems(String type, Collection<CacheData> items) {
    if (items.isEmpty()){
      return;
    }

    final Set<String> relationshipNames = new HashSet<>();
    final List<String> keysToSet = new LinkedList<>();
    final Set<String> idSet = new HashSet<>();

    final Map<String, Integer> ttlSecondsByKey = new HashMap<>();
    int skippedWrites = 0;

    final Map<String, String> hashes = getHashes(type, items);

    final NavigableMap<String, String> updatedHashes = new TreeMap<>();

    for (CacheData item : items) {
      MergeOp op = buildMergeOp(type, item, hashes);
      relationshipNames.addAll(op.relNames);
      keysToSet.addAll(op.keysToSet);
      idSet.add(item.getId());
      updatedHashes.putAll(op.hashesToSet);
      skippedWrites += op.skippedWrites;

      if (item.getTtlSeconds() > 0) {
        for (String key : op.keysToSet) {
          ttlSecondsByKey.put(key, item.getTtlSeconds());
        }
      }
    }

    int saddOperations = 0;
    int msetOperations = 0;
    int hmsetOperations = 0;
    int expireOperations = 0;
    if (!keysToSet.isEmpty()) {
      DynoJedisClient client = (DynoJedisClient) redisClientDelegate.getCommandsClient();
      for (List<String> idPart : Iterables.partition(idSet, options.getMaxSaddSize())) {
        final String[] ids = idPart.toArray(new String[idPart.size()]);
        client.sadd(allOfTypeReindex(type), ids);
        saddOperations++;
        client.sadd(allOfTypeId(type), ids);
        saddOperations++;
      }

      // TODO rz - refactor so we're just working with k/v maps of what to set
      for (List<String> keys : Lists.partition(keysToSet, options.getMaxMsetSize())) {
        // TODO dynomite - support mset
        int kn = keys.size() / 2;
        for (int i = 0; i < kn; i++) {
          client.set(keys.get(i), keys.get(i+1));
        }
//        client.mset(Arrays.copyOf(keys.toArray(), keys.size(), String[].class));
//        msetOperations++;
      }

      if (!relationshipNames.isEmpty()) {
        for (List<String> relNamesPart : Iterables.partition(relationshipNames, options.getMaxSaddSize())) {
          client.sadd(allRelationshipsId(type), relNamesPart.toArray(new String[relNamesPart.size()]));
          saddOperations++;
        }
      }

      if (!updatedHashes.isEmpty()) {
        for (List<String> hashPart : Iterables.partition(updatedHashes.keySet(), options.getMaxHmsetSize())) {
          client.hmset(hashesId(type), updatedHashes.subMap(hashPart.get(0), true, hashPart.get(hashPart.size() - 1), true));
          hmsetOperations++;
        }
      }

      for (Map.Entry<String, Integer> ttlEntry : ttlSecondsByKey.entrySet()) {
        client.expire(ttlEntry.getKey(), ttlEntry.getValue());
      }
      expireOperations += ttlSecondsByKey.size();
    }

    cacheMetrics.merge(
      prefix,
      type,
      items.size(),
      keysToSet.size() / 2,
      relationshipNames.size(),
      skippedWrites,
      updatedHashes.size(),
      saddOperations,
      msetOperations,
      hmsetOperations,
      0,
      expireOperations
    );
  }

  @Override
  protected void evictItems(String type, List<String> identifiers, Collection<String> allRelationships) {
    List<String> delKeys = new ArrayList<>((allRelationships.size() + 1) * identifiers.size());
    for (String id : identifiers) {
      for (String rel : allRelationships) {
        delKeys.add(relationshipId(type, id, rel));
      }
      delKeys.add(attributesId(type, id));
    }

    int delOperations = 0;
    int hdelOperations = 0;
    int sremOperations = 0;
    DynoJedisClient client = (DynoJedisClient) redisClientDelegate.getCommandsClient();
    for (List<String> delPartition : Lists.partition(delKeys, options.getMaxDelSize())) {
      client.del(delPartition.toArray(new String[delPartition.size()]));
      delOperations++;
      client.hdel(hashesId(type), (String[]) delPartition.toArray());
    }

    for (List<String> idPartition : Lists.partition(identifiers, options.getMaxDelSize())) {
      String[] ids = idPartition.toArray(new String[idPartition.size()]);
      client.srem(allOfTypeId(type), ids);
      sremOperations++;
      client.srem(allOfTypeReindex(type), ids);
      sremOperations++;
    }

    cacheMetrics.evict(
      prefix,
      type,
      identifiers.size(),
      delKeys.size(),
      delKeys.size(),
      delOperations,
      hdelOperations,
      sremOperations
    );
  }

  @Override
  protected Set<String> scanMembers(String setKey, Optional<String> glob) {
    DynoJedisClient client = (DynoJedisClient) redisClientDelegate.getCommandsClient();
    final Set<String> matches = new HashSet<>();
    final ScanParams scanParams = new ScanParams().count(options.getScanSize());
    glob.ifPresent(scanParams::match);
    String cursor = "0";
    while (true) {
      final ScanResult<String> scanResult = client.sscan(setKey, cursor, scanParams);
      matches.addAll(scanResult.getResult());
      cursor = scanResult.getStringCursor();
      if ("0".equals(cursor)) {
        return matches;
      }
    }
  }
}
