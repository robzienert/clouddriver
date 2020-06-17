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

package com.netflix.spinnaker.clouddriver.deploy.servergroup

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ResizeStrategySupportSpec extends Specification {

  @Shared
  Registry registry = Mock(Registry) {
    counter(_) >> Mock(Counter)
  }

  @Subject
  ResizeStrategySupport resizeStrategySupport = new ResizeStrategySupport([], registry)

  @Unroll
  def "test min logic in performScalingAndPinning() with unpinMin=#unpinMin originalMin=#originalMin"() {
    given:
    ResizeStrategy.ResizeCapacityCommand command = ResizeStrategy.ResizeCapacityCommand.builder()
      .unpinMinimumCapacity(unpinMin)
      .source(ResizeStrategy.Source.builder()
        .serverGroupName("app-v000")
        .capacity(new Capacity(originalMin, null, null))
        .build()
      )
      .build()

    when:
    def outputCapacity = resizeStrategySupport.performScalingAndPinning(sourceCapacity, command)

    then:
    outputCapacity == expectedCapacity

    where:
    sourceCapacity        | unpinMin | originalMin || expectedCapacity
    new Capacity(1, 3, 2) | false    | 1           || new Capacity(1, 3, 2)
    new Capacity(1, 3, 2) | true     | 1           || new Capacity(1, 3, 2)
    new Capacity(1, 3, 2) | true     | 2           || new Capacity(1, 3, 2) // won't unpin to a higher min 2
    new Capacity(1, 3, 2) | true     | 0           || new Capacity(0, 3, 2)
    new Capacity(1, 3, 2) | true     | null        || new Capacity(1, 3, 2)
    new Capacity(1, 3, 2) | true     | 0           || new Capacity(0, 3, 2) // verify that 0 is a valid originalMin
    new Capacity(1, 3, 2) | true     | null        || new Capacity(0, 3, 2) // picks the savedMin value
  }
}
