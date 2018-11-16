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

import java.util.UUID;

public class FederationWorker {

  String enqueue() {

    // Enqueue the scatter/gather work. This should be some internal proc that pulls work off a memory FIFO queue
    // A UUID will be returned as the work ID, which can be used to retrieve related work. Or should this just be
    // passed a consumer and that be that?

    return UUID.randomUUID().toString();
  }



}
