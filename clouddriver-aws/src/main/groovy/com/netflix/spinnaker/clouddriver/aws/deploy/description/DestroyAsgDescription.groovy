/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.description

class DestroyAsgDescription extends AbstractAmazonCredentialsDescription {

  String serverGroupName
  String region

  List<AsgDescription> asgs = []

  /**
   * If force is true, instances will be immediately terminated without lifecycle hooks, rather than letting the
   * AutoScaling service schedule terminations. This is the default behavior, but disabling it can be useful if
   * running lifecycle hooks is a requirement.
   */
  boolean force = true

  @Deprecated
  String asgName

  @Deprecated
  List<String> regions = []
}
