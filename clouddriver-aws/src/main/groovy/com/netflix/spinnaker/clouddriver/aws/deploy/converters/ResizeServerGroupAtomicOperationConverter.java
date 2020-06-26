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
package com.netflix.spinnaker.clouddriver.aws.deploy.converters;

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeServerGroupDescription;
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.ResizeServerGroupAtomicOperation;
import com.netflix.spinnaker.clouddriver.deploy.servergroup.ResizeStrategy;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import com.netflix.spinnaker.clouddriver.safety.TrafficGuard;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component("resizeServerGroupDescription")
@AmazonOperation(AtomicOperations.RESIZE_SERVER_GROUP)
public class ResizeServerGroupAtomicOperationConverter
    extends AbstractAtomicOperationsCredentialsSupport {

  private final ApplicationContext applicationContext;

  public ResizeServerGroupAtomicOperationConverter(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Nullable
  @Override
  public AtomicOperation convertOperation(Map input) {
    return convert(convertDescription(input));
  }

  @Nullable
  @Override
  public AtomicOperation convert(Object description) {
    // TODO(rz): Seems like a lot of this code could be abstracted away.
    if (!(description instanceof ResizeServerGroupDescription)) {
      throw new SystemException(
          format(
              "Description given is of type '%s', but '%s' is required",
              description.getClass().getName(), ResizeServerGroupDescription.class.getName()));
    }

    List<ResizeStrategy> resizeStrategies =
        new ArrayList<>(applicationContext.getBeansOfType(ResizeStrategy.class).values());

    AtomicOperation operation =
        new ResizeServerGroupAtomicOperation(
            (ResizeServerGroupDescription) description,
            applicationContext,
            resizeStrategies,
            applicationContext.getBean(TrafficGuard.class),
            applicationContext.getBean(RetryRegistry.class));

    applicationContext.getAutowireCapableBeanFactory().autowireBean(operation);

    return operation;
  }

  @Override
  public Object convertDescription(Map input) {
    ResizeServerGroupDescription description =
        getObjectMapper().convertValue(input, ResizeServerGroupDescription.class);
    description.setCredentials(getCredentialsObject((String) input.get("credentials")));
    return description;
  }
}
