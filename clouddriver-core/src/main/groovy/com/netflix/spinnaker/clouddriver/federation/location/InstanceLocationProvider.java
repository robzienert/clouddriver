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
package com.netflix.spinnaker.clouddriver.federation.location;

import javax.annotation.Nonnull;

/**
 * Allows automatic configuration of a Clouddriver's instance location.
 *
 * In the case of Amazon or Google, this implementation will use the instance
 * metadata URLs to return the location of the running process.
 *
 * If no location can be determined, the provider will fall back to static
 * config.
 */
public interface InstanceLocationProvider {

  @Nonnull
  String provide();
}
