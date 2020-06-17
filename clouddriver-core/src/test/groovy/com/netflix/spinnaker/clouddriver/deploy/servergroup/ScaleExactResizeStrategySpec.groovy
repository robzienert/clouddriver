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
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup.Capacity
import com.netflix.spinnaker.clouddriver.model.SimpleServerGroup
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.atomic.AtomicInteger

class ScaleExactResizeStrategySpec extends Specification {

  Registry registry = Mock() {
    counter(_) >> Mock(Counter)
  }
  ClusterProvider clusterProvider = Mock()

  ResizeStrategySupport resizeStrategySupport = new ResizeStrategySupport([clusterProvider], registry)

  ScaleExactResizeStrategy strategy = new ScaleExactResizeStrategy(resizeStrategySupport)

  @Unroll
  def "should derive capacity from ASG (#current) when partial values supplied in context (#specifiedCap)"() {
    setup:
    def command = commandBuilder()
      .serverGroupName(serverGroupName)
      .capacity(specifiedCap)
      .build()

    when:
    def cap = strategy.capacity(command)

    then:
    cap.original == current
    cap.target == expected
    1 * clusterProvider.getCloudProviderId() >> "aws"
    1 * clusterProvider.getServerGroup(account, region, serverGroupName) >> targetServerGroup
    0 * _

    where:
    specifiedCap                | current               || expected
    new Capacity(0, null, null) | new Capacity(1, 1, 1) || new Capacity(0, 1, 1)
    new Capacity(null, 0, null) | new Capacity(1, 1, 1) || new Capacity(0, 0, 0)
    new Capacity(null, 1, null) | new Capacity(1, 1, 1) || new Capacity(1, 1, 1)
    new Capacity(2, null, null) | new Capacity(1, 1, 1) || new Capacity(2, 2, 2)
    new Capacity(2, null, null) | new Capacity(1, 3, 1) || new Capacity(2, 3, 2)
    new Capacity(0, 2, null)    | new Capacity(1, 1, 1) || new Capacity(0, 2, 1)
    new Capacity(0, 2, null)    | new Capacity(1, 3, 3) || new Capacity(0, 2, 2)
    new Capacity(0, 2, null)    | new Capacity(1, 3, 3) || new Capacity(0, 2, 2)
    new Capacity(0, 2, 3)       | new Capacity(1, 3, 3) || new Capacity(0, 2, 3)
    new Capacity()              | new Capacity(1, 3, 3) || new Capacity(1, 3, 3)
    serverGroupName = asgName()
    targetServerGroup = new SimpleServerGroup(name: serverGroupName, region: region, type: cloudProvider, capacity: current)
  }

  @Unroll
  def "should return source server group capacity with scalePct=#scalePct pinCapacity=#pinCapacity pinMinimumCapacity=#pinMinimumCapacity"() {
    given:
    def command = commandBuilder(serverGroupName)
      .capacity(targetCapacity)
      .pinMinimumCapacity(pinMinimumCapacity)
      .pinCapacity(pinCapacity ?: false)
      .scalePct(scalePct)
      .build()

    when:
    def capacity = strategy.capacity(command)

    then:
    1 * clusterProvider.getCloudProviderId() >> "aws"
    1 * clusterProvider.getServerGroup(account, region, serverGroupName) >> targetServerGroup
    capacity.original == originalCapacity
    capacity.target == expectedCapacity

    where:
    scalePct | pinCapacity | pinMinimumCapacity | targetCapacity        || expectedCapacity
    null     | false       | false              | new Capacity(4, 6, 5) || new Capacity(4, 6, 5)
    null     | false       | true               | new Capacity(4, 6, 5) || new Capacity(5, 6, 5)
    null     | false       | false              | new Capacity(4, 6, 5) || new Capacity(4, 6, 5)
    100      | false       | false              | new Capacity(4, 6, 5) || new Capacity(4, 6, 5)
    50       | false       | false              | new Capacity(4, 6, 5) || new Capacity(3, 6, 3) // Math.ceil in scalePct
    25       | false       | false              | new Capacity(4, 6, 5) || new Capacity(2, 6, 2)
    20       | false       | false              | new Capacity(4, 6, 5) || new Capacity(1, 6, 1) // exact division
    0        | false       | false              | new Capacity(4, 6, 5) || new Capacity(0, 6, 0)
    0        | false       | false              | new Capacity(4, 6, 5) || new Capacity(0, 6, 0)
    100      | true        | false              | new Capacity(4, 6, 5) || new Capacity(5, 5, 5)
    50       | true        | false              | new Capacity(4, 6, 5) || new Capacity(3, 3, 3)
    25       | true        | false              | new Capacity(4, 6, 5) || new Capacity(2, 2, 2)
    0        | true        | false              | new Capacity(4, 6, 5) || new Capacity(0, 0, 0)

    serverGroupName = asgName()
    originalCapacity = new Capacity(max: 3, min: 1, desired: 3)
    targetServerGroup = new SimpleServerGroup(name: serverGroupName, region: region, type: cloudProvider, capacity: originalCapacity)
  }

  static final AtomicInteger asgSeq = new AtomicInteger(100)
  static final String cloudProvider = 'aws'
  static final String application = 'foo'
  static final String region = 'us-east-1'
  static final String account = 'test'
  static final String clusterName = application + '-main'

  static String asgName() {
    clusterName + '-v' + asgSeq.incrementAndGet()
  }

  private static ResizeStrategy.ResizeCapacityCommand.ResizeCapacityCommandBuilder commandBuilder(String serverGroupName) {
    return ResizeStrategy.ResizeCapacityCommand.builder()
      .action(ResizeStrategy.ResizeAction.SCALE_EXACT)
      .cloudProvider(cloudProvider)
      .credentials(account)
      .location(region)
      .serverGroupName(serverGroupName)
  }
}
