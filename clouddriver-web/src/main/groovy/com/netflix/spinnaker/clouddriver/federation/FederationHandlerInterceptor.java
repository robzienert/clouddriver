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

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Set;

/**
 * TODO(rz): Some endpoints will need to be routed to a single shard, others scatter/gather, others return the local
 * TODO(rz): Need some way of determining the location of the running instance / enabled accounts
 * TODO(rz): Need to support routing write operations
 */
public class FederationHandlerInterceptor implements HandlerInterceptor {

  private final static Logger log = LoggerFactory.getLogger(FederationHandlerInterceptor.class);

  private final static Set<String> LOCATION_KEYS = Sets.newHashSet("region", "location");
  private final static Set<String> ACCOUNT_KEYS = Sets.newHashSet("account");

//  private final ShardRegistry shardRegistry;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    String location = getLocation(request);
    String account = getAccount(request);

    if (location == null && account != null) {
      log.trace("No location found, but account present: Cannot route these sort of requests");
      return true;
    }

    // TODO rz - Ignore the operations handler.



    return false;


  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    // Do nothing.
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    // Do nothing.
  }

  private static String getLocation(HttpServletRequest request) {
    for (String key : LOCATION_KEYS) {
      String param = request.getParameter(key);
      if (param != null) {
        return param;
      }
    }
    return null;
  }

  private static String getAccount(HttpServletRequest request) {
    for (String key : ACCOUNT_KEYS) {
      String param = request.getParameter(key);
      if (param != null) {
        return param;
      }
    }
    return null;
  }
}
