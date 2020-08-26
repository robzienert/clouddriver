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
package com.netflix.spinnaker.clouddriver.saga.models

import com.netflix.spinnaker.clouddriver.saga.exceptions.SagaSystemException

class ActionResources(
  private val resources: List<ActionResource>
) {

  @Suppress("UNCHECKED_CAST")
  fun <T> get(name: String, type: Class<T>): T =
    resources
      .firstOrNull { it.type == type && it.name == name }
      ?.resource as T
      ?: throw SagaSystemException("Could not find '${type.simpleName}' resource with name '$name'")
}

data class ActionResource(
  val name: String,
  val type: Class<*>,
  val resource: Any
)

data class ResourceRef(
  val name: String,
  val type: Class<*>
)
