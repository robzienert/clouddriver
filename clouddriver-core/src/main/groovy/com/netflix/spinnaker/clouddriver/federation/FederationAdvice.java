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

import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer;
import com.netflix.spinnaker.clouddriver.scattergather.reducer.DeepMergeResponseReducer;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface FederationAdvice {
  /**
   * If set to true, the local instance will handle the request directly without additional routing
   */
  boolean local() default false;

  /**
   * An override of the ResponseReducer used for a specific endpoint or controller.
   */
  Class<? extends ResponseReducer> reducer() default DeepMergeResponseReducer.class;

  /**
   * Needed for some endpoints where the location is not using a standard name. (e.g. clusters endpoints use "scope")
   */
  String locationParameter() default "";
  String accountParameter() default "";
}
