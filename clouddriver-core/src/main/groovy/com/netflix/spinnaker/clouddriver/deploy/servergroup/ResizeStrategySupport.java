/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.clouddriver.deploy.servergroup;

import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Optional;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** Provides helper functions for {@link ResizeStrategy} implementations. */
@Component
@NonnullByDefault
public class ResizeStrategySupport {

  private final ApplicationContext applicationContext;

  public ResizeStrategySupport(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  /** Get the {@link ClusterProvider} for the given cloud provider, if one exists. */
  public Optional<ClusterProvider> clusterProviderForCloud(String cloudProvider) {
    return applicationContext.getBeansOfType(ClusterProvider.class).values().stream()
        .filter(it -> it.getCloudProviderId().equals(cloudProvider))
        .findFirst();
  }
}
