/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.orchestration.v1bridge

import de.huxhorn.sulky.ulid.ULID
import spock.lang.Specification
import spock.lang.Subject

import javax.annotation.Nonnull
import java.time.Duration

class OperationSagaBridgeSpec extends Specification {

  @Subject OperationSagaBuilder subject = new OperationSagaV1Bridge(new ULID().nextValue())

  def "wip - dsl"() {
    when:
    def result = subject
      .query("prepare next server group description", Duration.ofMillis(1)) { _ ->
        new StubResult(new PreparedServerGroup())
      }
      .command("Create launch config") { state ->
        PreparedServerGroup data = state.get(PreparedServerGroup)
        new StubResult(data)
      }
      .command("Disable scaling processes") { state ->
        SomeCommandCreatedResource data = state.get(SomeCommandCreatedResource)
        // Do stuff
        new StubResult("finished")
      }
      .execute(String)

    then:
    result == "finished"
  }
}

class PreparedServerGroup {
  PreparedServerGroup() {
  }

  Object getDescription() {
    return Arrays.asList("object1", "object2");
  }
}

class SomeCommandCreatedResource {
  SomeCommandCreatedResource() {
  }
}

class StubResult implements OperationSagaBuilder.CommandStepResult<Object> {

  private final Object result;

  StubResult(Object result) {
    this.result = result;
  }

  @Nonnull @Override
  Object getResult() {
    return result;
  }

  @Override
  Exception getError() {
    return null
  }

  @Override
  boolean getRetryable() {
    return true;
  }
}
