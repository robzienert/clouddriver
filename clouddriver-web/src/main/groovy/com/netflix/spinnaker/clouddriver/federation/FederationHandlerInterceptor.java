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
 * TODO(rz): Some endpoints will need to be routed to a single shard, others scatter/gather, others return the local
 * TODO(rz): Need to support routing write operations
 */
public class FederationHandlerInterceptor implements HandlerInterceptor {

  private final static Logger log = LoggerFactory.getLogger(FederationHandlerInterceptor.class);

  private final static Set<String> LOCATION_KEYS = Sets.newHashSet("region", "location");
  private final static Set<String> ACCOUNT_KEYS = Sets.newHashSet("account");

  private final ShardConfigurationProvider shardConfigurationProvider;
  private final ScatterGather scatterGather;
  private final FederationWorker federationWorker;

  public FederationHandlerInterceptor(ShardConfigurationProvider shardConfigurationProvider,
                                      ScatterGather scatterGather) {
    // TODO(rz): wire
    this.shardConfigurationProvider = shardConfigurationProvider;
    this.scatterGather = scatterGather;
    federationWorker = new FederationWorker();
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
    String location = getVariable(LOCATION_KEYS, requestVariables);
    String account = getVariable(ACCOUNT_KEYS, requestVariables);

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

    // TODO(rz): What if this instance needs to service the request as well? I'll need to push all of this scatter
    // gather stuff for other shards into a separate process, handle the request as it exists, then merge things
    // together in postHandle?

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
    if (isScatteredRequest(request)) {
      // If the request is already scattered, we should just return the response as-is.
      return;
    }

    // IMPORTANT: This code path will only be used if the local instance needed to service the request as well.
//    ContentCachingResponseWrapper responseWrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
//
//    String localResponse = IOUtils.toString(responseWrapper.getContentInputStream());
//    responseWrapper.reset();

    // TODO(rz): And then here I can pull together everything via the reducers...
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

  private FederationAdvice getHandlerFederationAdvice(HandlerMethod method) {
    FederationAdvice advice = method.getMethodAnnotation(FederationAdvice.class);
    if (advice != null) {
      return advice;
    }
    // TODO rz - Support class-level

    return null;
  }

  private boolean hasLocalAdvice(HandlerMethod method) {
    FederationAdvice advice = getHandlerFederationAdvice(method);
    if (advice == null) {
      return false;
    }
    return advice.local();
  }

  private ResponseReducer getReducer(HandlerMethod method) {
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

  private boolean isScatteredRequest(HttpServletRequest request) {
    return request.getHeader("X-Spinnaker-ScatteredRequest") != null;
  }
}
