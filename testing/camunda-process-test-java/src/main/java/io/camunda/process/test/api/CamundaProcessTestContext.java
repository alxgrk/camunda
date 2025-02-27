/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/** The injected context for a process test. */
public interface CamundaProcessTestContext {

  /**
   * Creates a new preconfigured Camunda client that is managed by the runtime.
   *
   * @return a new Camunda client
   */
  ZeebeClient createClient();

  /**
   * Creates a new preconfigured Camunda client that is managed by the runtime. The given modifier
   * can customize the client.
   *
   * @param modifier to customize the Camunda client
   * @return a new Camunda client
   */
  ZeebeClient createClient(final Consumer<ZeebeClientBuilder> modifier);

  /**
   * @return the URI of Zeebe's gRPC API address
   */
  URI getZeebeGrpcAddress();

  /**
   * @return the URI of Zeebe's REST API address
   */
  URI getZeebeRestAddress();

  /**
   * The current time may differ from the system time if the time was modified using {@link
   * #increaseTime(Duration)}.
   *
   * @return the current time for the process tests
   */
  Instant getCurrentTime();

  /**
   * Modifies the current time for the process tests. It can be used to jump to the future to avoid
   * waiting until a due date is reached, for example, of a BPMN timer event.
   *
   * @param timeToAdd the duration to add to the current time
   */
  void increaseTime(final Duration timeToAdd);
}
