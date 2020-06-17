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

import static java.lang.String.format;

import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationDescriptionPreProcessor;
import java.util.HashMap;
import java.util.Map;

public class ResizeServerGroupPreprocessor implements AtomicOperationDescriptionPreProcessor {

  @Override
  public boolean supports(Class descriptionClass) {
    return ResizeServerGroupDescription.class.isAssignableFrom(descriptionClass);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map process(Map description) {
    processMultipleSourceCapacityValues((Map<String, Object>) description);
    return description;
  }

  /**
   * Orca isn't consistent when it comes to defining where the source capacity is actually defined.
   * This method will normalize to a single location so that downstream logic is simpler.
   */
  @SuppressWarnings("unchecked")
  private void processMultipleSourceCapacityValues(Map<String, Object> description) {
    String serverGroupName = (String) description.get("serverGroupName");
    if (serverGroupName == null) {
      throw new IllegalStateException("A serverGroupName is required to resize");
    }

    String originalCapacityKey = format("originalCapacity.%s", serverGroupName);
    if (description.containsKey(originalCapacityKey)) {
      addSourceCapacity(description, (Map<String, Object>) description.get(originalCapacityKey));
      description.remove(originalCapacityKey);
    }
    if (description.containsKey("savedCapacity")) {
      addSourceCapacity(description, (Map<String, Object>) description.get("savedCapacity"));
      description.remove("savedCapacity");
    }
  }

  @SuppressWarnings("unchecked")
  private void addSourceCapacity(
      Map<String, Object> description, Map<String, Object> otherCapacity) {
    if (!description.containsKey("source")) {
      description.put("source", new HashMap<String, Object>());
    }
    Map<String, Object> source = (Map<String, Object>) description.get("source");
    source.put("capacity", otherCapacity);
  }
}
