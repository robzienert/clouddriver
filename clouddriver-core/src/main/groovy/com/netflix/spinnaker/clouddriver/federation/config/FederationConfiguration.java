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

import com.netflix.spinnaker.clouddriver.federation.location.DefaultOperationLocationResolver;
import com.netflix.spinnaker.clouddriver.federation.FederatingOrchestrationProcessor;
import com.netflix.spinnaker.clouddriver.federation.location.OperationLocationResolver;
import com.netflix.spinnaker.clouddriver.federation.location.InstanceLocationProvider;
import com.netflix.spinnaker.clouddriver.federation.location.StaticInstanceLocationProvider;
import com.netflix.spinnaker.clouddriver.orchestration.DefaultOrchestrationProcessor;
import com.netflix.spinnaker.clouddriver.orchestration.OrchestrationProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Optional;

@Configuration
@ConditionalOnExpression("${federation.enabled:false}")
@EnableConfigurationProperties(FederationConfigurationProperties.class)
public class FederationConfiguration {

  @Bean
  @ConditionalOnMissingBean(OperationLocationResolver.class)
  OperationLocationResolver operationLocationResolver() {
    return new DefaultOperationLocationResolver();
  }

  @Bean
  @ConditionalOnMissingBean(InstanceLocationProvider.class)
  InstanceLocationProvider instanceLocationProvider(FederationConfigurationProperties properties) {
    return new StaticInstanceLocationProvider(properties);
  }

  /**
   * When federation is enabled, we force use of the {@code FederatingOrchestrationProcessor}. If a custom
   * {@code OrchestrationProcessor} is provided, it will be used in place of the default when routing is
   * not required.
   */
  @Bean
  @Primary
  OrchestrationProcessor federatingOrchestrationProcessor(OperationLocationResolver operationLocationResolver,
                                                          InstanceLocationProvider instanceLocationProvider,
                                                          Optional<OrchestrationProcessor> orchestrationProcessor) {
    return new FederatingOrchestrationProcessor(
      operationLocationResolver,
      instanceLocationProvider,
      orchestrationProcessor.orElse(new DefaultOrchestrationProcessor())
    );
  }
}
