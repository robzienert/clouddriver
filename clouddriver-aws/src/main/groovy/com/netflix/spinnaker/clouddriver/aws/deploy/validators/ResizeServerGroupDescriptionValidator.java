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
package com.netflix.spinnaker.clouddriver.aws.deploy.validators;

import com.netflix.spinnaker.clouddriver.aws.AmazonOperation;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.ResizeServerGroupDescription;
import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

@Component("resizeServerGroupDescriptionValidator")
@AmazonOperation(AtomicOperations.RESIZE_SERVER_GROUP)
public class ResizeServerGroupDescriptionValidator
    extends DescriptionValidator<ResizeServerGroupDescription> {
  @Override
  public void validate(
      List priorDescriptions, ResizeServerGroupDescription description, Errors errors) {}
}
