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
import com.netflix.spinnaker.clouddriver.federation.config.Shard;
import com.netflix.spinnaker.clouddriver.federation.config.ShardConfigurationProvider;
import com.netflix.spinnaker.clouddriver.scattergather.*;
import com.netflix.spinnaker.clouddriver.scattergather.reducer.DeepMergeResponseReducer;
import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse;
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Currently does not do local process dispatching. If the local shard is needed to fulfill a request, a separate
 * call is made and merged in. Local dispatching is a future enhancement.
 *
 * TODO(rz): Some endpoints will need to be routed to a single shard, others scatter/gather, others return the local
 * TODO(rz): Need to support routing write operations
 */
public class FederationHandlerInterceptor implements HandlerInterceptor {

  private final static Logger log = LoggerFactory.getLogger(FederationHandlerInterceptor.class);

  private final static Set<String> LOCATION_KEYS = Sets.newHashSet("region", "location");
  private final static Set<String> ACCOUNT_KEYS = Sets.newHashSet("account");

  private final ShardConfigurationProvider shardConfigurationProvider;
  private final ScatterGather scatterGather;

  public FederationHandlerInterceptor(ShardConfigurationProvider shardConfigurationProvider,
                                      ScatterGather scatterGather) {
    this.shardConfigurationProvider = shardConfigurationProvider;
    this.scatterGather = scatterGather;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
    if (isScatteredRequest(request)) {
      // Handling a request that has already been routed by another shard. Move on with life.
      return true;
    }

    HandlerMethod handlerMethod = getHandlerMethod(handler);
    if (handlerMethod == null) {
      log.trace("Unknown handler type: {} for request: {}", handler.getClass().getSimpleName(), request);
      return true;
    }

    // For handlers that have been annotated with FederationAdvice(local = true), we'll just short-circuit now.
    if (hasLocalAdvice(handlerMethod)) {
      return true;
    }

    // Extract information from the handler that will be required for routing.
    Map<String, Object> requestVariables = getRequestVariables(request);
    String location = getLocation(handlerMethod, requestVariables);
    String account = getAccount(handlerMethod, requestVariables);

    // Look up the shard(s) that will be responsible for actually servicing the requests.
    List<Shard> shards;
    if (location == null && account == null) {
      shards = shardConfigurationProvider.allShards();
    } else {
      shards = shardConfigurationProvider.findShards(location, account);
    }

    if (shards.isEmpty()) {
      response.sendError(500, format("No shard configured for location: %s, account: %s", location, account));
      return false;
    }
    log.debug("Selected shards: {}", shards.stream().map(Shard::getName).collect(Collectors.toList()));

    ServletScatterGatherRequest scatterRequest = new ServletScatterGatherRequest(
      shards.stream().collect(Collectors.toMap(Shard::getName, Shard::getBaseUrl)),
      request
    );

    // TODO(rz): Need to pass along authz?
    ReducedResponse reducedResponse = scatterGather.request(scatterRequest, getReducer(handlerMethod));
    if (reducedResponse.isError()) {
      response.sendError(reducedResponse.getStatus(), reducedResponse.getBody());
      return false;
    }

    reducedResponse.applyTo(response).flush();

    return false;
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws IOException {
    // Do nothing.
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    // Do nothing.
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> getRequestVariables(HttpServletRequest request) {
    Object uriTemplateVariables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (uriTemplateVariables instanceof Map) {
      return new HashMap<>((Map<String, Object>) uriTemplateVariables);
    }
    return Collections.EMPTY_MAP;
  }

  private static String getLocation(HandlerMethod handlerMethod, Map<String, Object> requestVariables) {
    FederationAdvice advice = getHandlerFederationAdvice(handlerMethod);
    if (advice != null && !advice.locationParameter().isEmpty()) {
      return getVariable(Sets.newHashSet(advice.locationParameter()), requestVariables);
    }
    return getVariable(LOCATION_KEYS, requestVariables);
  }

  private static String getAccount(HandlerMethod handlerMethod, Map<String, Object> requestVariables) {
    FederationAdvice advice = getHandlerFederationAdvice(handlerMethod);
    if (advice != null && !advice.accountParameter().isEmpty()) {
      return getVariable(Sets.newHashSet(advice.accountParameter()), requestVariables);
    }
    return getVariable(ACCOUNT_KEYS, requestVariables);
  }

  private static String getVariable(Set<String> names, Map<String, Object> variables) {
    for (String name : names) {
      Object variable = variables.get(name);
      if (variable != null) {
        return (String) variable;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static HandlerMethod getHandlerMethod(Object handler) {
    if (!(handler instanceof HandlerMethod)) {
      return null;
    }
    return (HandlerMethod) handler;
  }

  private static FederationAdvice getHandlerFederationAdvice(HandlerMethod method) {
    FederationAdvice advice = method.getMethodAnnotation(FederationAdvice.class);
    if (advice != null) {
      return advice;
    }
    // TODO rz - Support class-level

    return null;
  }

  private static boolean hasLocalAdvice(HandlerMethod method) {
    FederationAdvice advice = getHandlerFederationAdvice(method);
    if (advice == null) {
      return false;
    }
    return advice.local();
  }

  private static ResponseReducer getReducer(HandlerMethod method) {
    FederationAdvice advice = getHandlerFederationAdvice(method);
    if (advice == null) {
      return new DeepMergeResponseReducer();
    }
    try {
      return advice.reducer().newInstance();
    } catch (InstantiationException|IllegalAccessException e) {
      // This shouldn't happen.
      // TODO(rz): Need to do more here.
      throw new IllegalStateException(e);
    }
  }

  private static boolean isScatteredRequest(HttpServletRequest request) {
    return request.getHeader("X-Spinnaker-ScatteredRequest") != null;
  }
}
