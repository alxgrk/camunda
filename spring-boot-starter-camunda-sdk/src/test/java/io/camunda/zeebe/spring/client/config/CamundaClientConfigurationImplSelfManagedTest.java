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
package io.camunda.zeebe.spring.client.config;

import static io.camunda.zeebe.spring.client.properties.ZeebeClientConfigurationProperties.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.oauth.OAuthCredentialsProvider;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientAllAutoConfiguration;
import io.camunda.zeebe.spring.client.configuration.ZeebeClientProdAutoConfiguration;
import io.camunda.zeebe.spring.client.jobhandling.ZeebeClientExecutorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.OutputCaptureExtension;

@SpringBootTest(
    classes = {ZeebeClientAllAutoConfiguration.class, ZeebeClientProdAutoConfiguration.class},
    properties = {
      "camunda.client.mode=self-managed",
      "camunda.client.auth.client-id=my-client-id",
      "camunda.client.auth.client-secret=my-client-secret"
    })
@ExtendWith(OutputCaptureExtension.class)
public class CamundaClientConfigurationImplSelfManagedTest {
  @Autowired CamundaClientConfiguration camundaClientConfiguration;
  @Autowired JsonMapper jsonMapper;
  @Autowired ZeebeClientExecutorService zeebeClientExecutorService;

  @Test
  void shouldContainsZeebeClientConfiguration() {
    assertThat(camundaClientConfiguration).isNotNull();
  }

  @Test
  void shouldNotHaveCredentialsProvider() {
    assertThat(camundaClientConfiguration.getCredentialsProvider())
        .isInstanceOf(OAuthCredentialsProvider.class);
  }

  @Test
  void shouldHaveGatewayAddress() {
    assertThat(camundaClientConfiguration.getGatewayAddress()).isEqualTo("localhost:26500");
  }

  @Test
  void shouldHaveDefaultTenantId() {
    assertThat(camundaClientConfiguration.getDefaultTenantId())
        .isEqualTo(DEFAULT.getDefaultTenantId());
  }

  @Test
  void shouldHaveDefaultJobWorkerTenantIds() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerTenantIds())
        .isEqualTo(DEFAULT.getDefaultJobWorkerTenantIds());
  }

  @Test
  void shouldHaveNumJobWorkerExecutionThreads() {
    assertThat(camundaClientConfiguration.getNumJobWorkerExecutionThreads())
        .isEqualTo(DEFAULT.getNumJobWorkerExecutionThreads());
  }

  @Test
  void shouldHaveDefaultJobWorkerMaxJobsActive() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerMaxJobsActive())
        .isEqualTo(DEFAULT.getDefaultJobWorkerMaxJobsActive());
  }

  @Test
  void shouldHaveDefaultJobWorkerName() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerName())
        .isEqualTo(DEFAULT.getDefaultJobWorkerName());
  }

  @Test
  void shouldHaveDefaultJobTimeout() {
    assertThat(camundaClientConfiguration.getDefaultJobTimeout())
        .isEqualTo(DEFAULT.getDefaultJobTimeout());
  }

  @Test
  void shouldHaveDefaultJobPollInterval() {
    assertThat(camundaClientConfiguration.getDefaultJobPollInterval())
        .isEqualTo(DEFAULT.getDefaultJobPollInterval());
  }

  @Test
  void shouldHaveDefaultMessageTimeToLive() {
    assertThat(camundaClientConfiguration.getDefaultMessageTimeToLive())
        .isEqualTo(DEFAULT.getDefaultMessageTimeToLive());
  }

  @Test
  void shouldHaveDefaultRequestTimeout() {
    assertThat(camundaClientConfiguration.getDefaultRequestTimeout())
        .isEqualTo(DEFAULT.getDefaultRequestTimeout());
  }

  @Test
  void shouldHavePlaintextConnectionEnabled() {
    assertThat(camundaClientConfiguration.isPlaintextConnectionEnabled()).isEqualTo(true);
  }

  @Test
  void shouldHaveCaCertificatePath() {
    assertThat(camundaClientConfiguration.getCaCertificatePath())
        .isEqualTo(DEFAULT.getCaCertificatePath());
  }

  @Test
  void shouldHaveKeepAlive() {
    assertThat(camundaClientConfiguration.getKeepAlive()).isEqualTo(DEFAULT.getKeepAlive());
  }

  @Test
  void shouldNotHaveClientInterceptors() {
    assertThat(camundaClientConfiguration.getInterceptors()).isEmpty();
  }

  @Test
  void shouldHaveJsonMapper() {
    assertThat(camundaClientConfiguration.getJsonMapper()).isEqualTo(jsonMapper);
  }

  @Test
  void shouldHaveOverrideAuthority() {
    assertThat(camundaClientConfiguration.getOverrideAuthority())
        .isEqualTo(DEFAULT.getOverrideAuthority());
  }

  @Test
  void shouldHaveMaxMessageSize() {
    assertThat(camundaClientConfiguration.getMaxMessageSize())
        .isEqualTo(DEFAULT.getMaxMessageSize());
  }

  @Test
  void shouldHaveMaxMetadataSize() {
    assertThat(camundaClientConfiguration.getMaxMetadataSize())
        .isEqualTo(DEFAULT.getMaxMetadataSize());
  }

  @Test
  void shouldHaveJobWorkerExecutor() {
    assertThat(camundaClientConfiguration.jobWorkerExecutor())
        .isEqualTo(zeebeClientExecutorService.get());
  }

  @Test
  void shouldHaveOwnsJobWorkerExecutor() {
    assertThat(camundaClientConfiguration.ownsJobWorkerExecutor()).isEqualTo(true);
  }

  @Test
  void shouldHaveDefaultJobWorkerStreamEnabled() {
    assertThat(camundaClientConfiguration.getDefaultJobWorkerStreamEnabled())
        .isEqualTo(DEFAULT.getDefaultJobWorkerStreamEnabled());
  }

  @Test
  void shouldHaveDefaultRetryPolicy() {
    assertThat(camundaClientConfiguration.useDefaultRetryPolicy())
        .isEqualTo(DEFAULT.useDefaultRetryPolicy());
  }
}
