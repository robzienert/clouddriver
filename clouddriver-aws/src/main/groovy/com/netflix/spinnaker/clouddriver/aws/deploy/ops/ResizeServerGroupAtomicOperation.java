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
package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import static java.lang.String.format;

import com.google.common.collect.ImmutableList;
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.ResizeAsgAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.ResumeAsgProcessesAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.converters.SuspendAsgProcessesAtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeAsgDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeServerGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResumeAsgProcessesDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.SuspendAsgProcessesDescription;
import com.netflix.spinnaker.clouddriver.deploy.servergroup.ResizeStrategy;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.clouddriver.safety.TrafficGuard;
import com.netflix.spinnaker.kork.exceptions.IntegrationException;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.context.ApplicationContext;

public class ResizeServerGroupAtomicOperation implements AtomicOperation<Void> {

  private static final List<String> SCALING_PROCESS_SUSPENSIONS =
      ImmutableList.of("Launch", "Template");

  private final ResizeServerGroupDescription description;
  private final ApplicationContext applicationContext;
  private final List<ResizeStrategy> resizeStrategies;
  private final TrafficGuard trafficGuard;
  private final Retry trafficGuardRetry;

  public ResizeServerGroupAtomicOperation(
      ResizeServerGroupDescription description,
      ApplicationContext applicationContext,
      List<ResizeStrategy> resizeStrategies,
      TrafficGuard trafficGuard,
      RetryRegistry retryRegistry) {
    this.description = description;
    this.applicationContext = applicationContext;
    this.resizeStrategies = resizeStrategies;
    this.trafficGuard = trafficGuard;
    this.trafficGuardRetry =
        retryRegistry.retry(
            "traffic-guards-verify-traffic-removal",
            RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofSeconds(30)).build());
  }

  @Override
  public Void operate(List priorOutputs) {
    ResizeStrategy.CapacitySet capacitySet =
        getResizeStrategy().capacity(description.toResizeCapacityCommand());

    trafficGuardRetry.executeRunnable(
        () ->
            trafficGuard.verifyTrafficRemoval(
                description.getCredentials().getCloudProvider(),
                description.getAccount(),
                description.getLocation(),
                description.getServerGroupName(),
                "Removal of all instances"));

    ResizeAsgDescription.AsgTargetDescription targetAsg =
        new ResizeAsgDescription.AsgTargetDescription();
    targetAsg.setCapacity(capacitySet.getTarget());

    ResizeAsgDescription.Constraints constraints = new ResizeAsgDescription.Constraints();
    constraints.setCapacity(capacitySet.getOriginal());
    targetAsg.setConstraints(constraints);

    ResizeAsgDescription resizeDescription = new ResizeAsgDescription();
    resizeDescription.setAsgs(Collections.singletonList(targetAsg));
    resizeDescription.setCredentials(description.getCredentials());

    AtomicOperationConverter converter =
        applicationContext.getBean(ResizeAsgAtomicOperationConverter.class);
    AtomicOperation operation = converter.convertOperation(resizeDescription);

    operation.operate(priorOutputs);

    return null;
  }

  private ResizeStrategy getResizeStrategy() {
    return resizeStrategies.stream()
        .filter(it -> it.handles(description.getStrategy()))
        .findFirst()
        .orElseThrow(
            () ->
                new IntegrationException(
                    format("No strategy implemented for '%s'", description.getStrategy())));
  }

  @Override
  public boolean supportsLifecycle(OperationLifecycle lifecycle) {
    return true;
  }

  @Nullable
  @Override
  public Object beforeOperate() {
    AtomicOperationConverter converter =
        applicationContext.getBean(SuspendAsgProcessesAtomicOperationConverter.class);

    SuspendAsgProcessesDescription suspendDescription = new SuspendAsgProcessesDescription();
    suspendDescription.setCredentials(description.getCredentials());
    suspendDescription.setServerGroupName(description.getServerGroupName());
    suspendDescription.setRegion(description.getLocation());
    suspendDescription.setProcesses(SCALING_PROCESS_SUSPENSIONS);

    AtomicOperation operation = converter.convertOperation(suspendDescription);
    operation.operate(Collections.emptyList());

    return null;
  }

  @Nullable
  @Override
  public Object afterOperate() {
    AtomicOperationConverter converter =
        applicationContext.getBean(ResumeAsgProcessesAtomicOperationConverter.class);

    ResumeAsgProcessesDescription resumeDescription = new ResumeAsgProcessesDescription();
    resumeDescription.setCredentials(description.getCredentials());
    resumeDescription.setServerGroupName(description.getServerGroupName());
    resumeDescription.setRegion(description.getLocation());
    resumeDescription.setProcesses(SCALING_PROCESS_SUSPENSIONS);

    AtomicOperation operation = converter.convertOperation(resumeDescription);
    operation.operate(Collections.emptyList());

    return null;
  }
}
