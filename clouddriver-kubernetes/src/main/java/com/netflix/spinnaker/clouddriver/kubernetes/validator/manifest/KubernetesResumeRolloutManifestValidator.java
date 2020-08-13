/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.validator.manifest;

import static com.netflix.spinnaker.clouddriver.orchestration.AtomicOperations.RESUME_ROLLOUT_MANIFEST;

import com.netflix.spinnaker.clouddriver.deploy.DescriptionValidator;
import com.netflix.spinnaker.clouddriver.deploy.ValidationErrors;
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesOperation;
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesResumeRolloutManifestDescription;
import com.netflix.spinnaker.clouddriver.kubernetes.validator.KubernetesValidationUtil;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@KubernetesOperation(RESUME_ROLLOUT_MANIFEST)
@Component
public class KubernetesResumeRolloutManifestValidator
    extends DescriptionValidator<KubernetesResumeRolloutManifestDescription> {
  @Autowired AccountCredentialsProvider provider;

  @Override
  public void validate(
      List priorDescriptions,
      KubernetesResumeRolloutManifestDescription description,
      ValidationErrors errors) {
    KubernetesValidationUtil util =
        new KubernetesValidationUtil("resumeRolloutKubernetesManifest", errors);
    if (!util.validateCredentials(
        provider,
        description.getAccount(),
        description.getPointCoordinates().getKind(),
        description.getPointCoordinates().getNamespace())) {
      return;
    }
  }
}
