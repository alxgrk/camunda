/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.connect;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.opensearch.ExtendedOpenSearchClient;
import io.camunda.operate.property.OpensearchProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.property.SslProperties;
import io.camunda.plugin.search.header.DatabaseCustomHeaderSupplier;
import io.camunda.zeebe.util.ReflectUtil;
import io.camunda.zeebe.util.jar.ExternalJarClassLoader;
import io.camunda.zeebe.util.jar.ThreadContextUtil;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLContext;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.elasticsearch.ElasticsearchException;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;

@Configuration
@Conditional(OpensearchCondition.class)
public class OpensearchConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpensearchConnector.class);

  private final OperateProperties operateProperties;

  private final ObjectMapper objectMapper;

  public OpensearchConnector(
      final OperateProperties operateProperties,
      @Qualifier("operateObjectMapper") final ObjectMapper objectMapper) {
    this.operateProperties = operateProperties;
    this.objectMapper = objectMapper;
  }

  @Bean
  @Primary
  public OpenSearchClient openSearchClient() {
    final OpenSearchClient openSearchClient = createOsClient(operateProperties.getOpensearch());
    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (final IOException e) {
      LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
    }
    return openSearchClient;
  }

  @Bean
  public OpenSearchAsyncClient openSearchAsyncClient() {
    final OpenSearchAsyncClient openSearchClient =
        createAsyncOsClient(operateProperties.getOpensearch());
    final CompletableFuture<HealthResponse> healthResponse;
    try {
      healthResponse = openSearchClient.cluster().health();
      healthResponse.whenComplete(
          (response, e) -> {
            if (e != null) {
              LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
            } else {
              LOGGER.info("OpenSearch cluster health: {}", response.status());
            }
          });
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    return openSearchClient;
  }

  @Bean("zeebeOpensearchClient")
  public OpenSearchClient zeebeOpensearchClient() {
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    return createOsClient(operateProperties.getZeebeOpensearch());
  }

  public OpenSearchAsyncClient createAsyncOsClient(final OpensearchProperties osConfig) {
    LOGGER.debug("Creating Async OpenSearch connection...");
    LOGGER.debug("Creating OpenSearch connection...");
    if (isAws()) {
      return getAwsAsyncClient(osConfig);
    }
    final HttpHost host = getHttpHost(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(httpClientBuilder, osConfig);
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, osConfig);
          return requestConfigBuilder;
        });

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
    jsonpMapper.objectMapper().registerModule(new JavaTimeModule());
    builder.setMapper(jsonpMapper);

    final OpenSearchTransport transport = builder.build();
    final OpenSearchAsyncClient openSearchAsyncClient = new OpenSearchAsyncClient(transport);

    final CompletableFuture<HealthResponse> healthResponse;
    try {
      healthResponse = openSearchAsyncClient.cluster().health();
      healthResponse.whenComplete(
          (response, e) -> {
            if (e != null) {
              LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
            } else {
              LOGGER.info("OpenSearch cluster health: {}", response.status());
            }
          });
    } catch (final IOException e) {
      throw new OperateRuntimeException(e);
    }

    if (!checkHealth(openSearchAsyncClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("OpenSearch connection was successfully created.");
    }
    return openSearchAsyncClient;
  }

  private boolean isAws() {
    final AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
    try {
      credentialsProvider.resolveCredentials();
      LOGGER.info("AWS Credentials can be resolved. Use AWS Opensearch");
      return true;
    } catch (final Exception e) {
      LOGGER.warn("AWS not configured due to: {} ", e.getMessage());
      return false;
    }
  }

  public OpenSearchClient createOsClient(final OpensearchProperties osConfig) {
    LOGGER.debug("Creating OpenSearch connection...");
    if (isAws()) {
      return getAwsClient(osConfig);
    }
    final HttpHost host = getHttpHost(osConfig);
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          configureHttpClient(httpClientBuilder, osConfig);
          return httpClientBuilder;
        });

    builder.setRequestConfigCallback(
        requestConfigBuilder -> {
          setTimeouts(requestConfigBuilder, osConfig);
          return requestConfigBuilder;
        });

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(objectMapper);
    builder.setMapper(jsonpMapper);

    final OpenSearchTransport transport = builder.build();
    final OpenSearchClient openSearchClient = new ExtendedOpenSearchClient(transport);
    try {
      final HealthResponse response = openSearchClient.cluster().health();
      LOGGER.info("OpenSearch cluster health: {}", response.status());
    } catch (final IOException e) {
      LOGGER.error("Error in getting health status from {}", "localhost:9205", e);
    }

    if (!checkHealth(openSearchClient)) {
      LOGGER.warn("OpenSearch cluster is not accessible");
    } else {
      LOGGER.debug("Elasticsearch connection was successfully created.");
    }
    return openSearchClient;
  }

  private OpenSearchClient getAwsClient(final OpensearchProperties osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            osConfig.getHost(),
            Region.of(region),
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .build());
    return new ExtendedOpenSearchClient(transport);
  }

  private OpenSearchAsyncClient getAwsAsyncClient(final OpensearchProperties osConfig) {
    final String region = new DefaultAwsRegionProviderChain().getRegion();
    final SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder().build();
    final AwsSdk2Transport transport =
        new AwsSdk2Transport(
            httpClient,
            osConfig.getHost(),
            Region.of(region),
            AwsSdk2TransportOptions.builder()
                .setMapper(new JacksonJsonpMapper(objectMapper))
                .build());
    return new OpenSearchAsyncClient(transport);
  }

  private HttpHost getHttpHost(final OpensearchProperties osConfig) {
    try {
      final URI uri = new URI(osConfig.getUrl());
      return new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
    } catch (final URISyntaxException e) {
      throw new OperateRuntimeException("Error in url: " + osConfig.getUrl(), e);
    }
  }

  private HttpAsyncClientBuilder setupAuthentication(
      final HttpAsyncClientBuilder builder, final OpensearchProperties osConfig) {
    if (!StringUtils.hasText(osConfig.getUsername())
        || !StringUtils.hasText(osConfig.getPassword())) {
      LOGGER.warn(
          "Username and/or password for are empty. Basic authentication for OpenSearch is not used.");
      return builder;
    }

    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    credentialsProvider.setCredentials(
        new AuthScope(getHttpHost(osConfig)),
        new UsernamePasswordCredentials(
            osConfig.getUsername(), osConfig.getPassword().toCharArray()));

    builder.setDefaultCredentialsProvider(credentialsProvider);
    return builder;
  }

  private void setupSSLContext(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final SslProperties sslConfig) {
    try {
      final ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
      tlsStrategyBuilder.setSslContext(getSSLContext(sslConfig));
      if (!sslConfig.isVerifyHostname()) {
        tlsStrategyBuilder.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      }

      final TlsStrategy tlsStrategy = tlsStrategyBuilder.build();
      final PoolingAsyncClientConnectionManager connectionManager =
          PoolingAsyncClientConnectionManagerBuilder.create().setTlsStrategy(tlsStrategy).build();

      httpAsyncClientBuilder.setConnectionManager(connectionManager);

    } catch (final Exception e) {
      LOGGER.error("Error in setting up SSLContext", e);
    }
  }

  private SSLContext getSSLContext(final SslProperties sslConfig)
      throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
    final KeyStore truststore = loadCustomTrustStore(sslConfig);
    final TrustStrategy trustStrategy =
        sslConfig.isSelfSigned() ? new TrustSelfSignedStrategy() : null; // default;
    if (truststore.size() > 0) {
      return SSLContexts.custom().loadTrustMaterial(truststore, trustStrategy).build();
    } else {
      // default if custom truststore is empty
      return SSLContext.getDefault();
    }
  }

  private KeyStore loadCustomTrustStore(final SslProperties sslConfig) {
    try {
      final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null);
      // load custom es server certificate if configured
      final String serverCertificate = sslConfig.getCertificatePath();
      if (serverCertificate != null) {
        setCertificateInTrustStore(trustStore, serverCertificate);
      }
      return trustStore;
    } catch (final Exception e) {
      final String message =
          "Could not create certificate trustStore for the secured OpenSearch Connection!";
      throw new OperateRuntimeException(message, e);
    }
  }

  private void setCertificateInTrustStore(
      final KeyStore trustStore, final String serverCertificate) {
    try {
      final Certificate cert = loadCertificateFromPath(serverCertificate);
      trustStore.setCertificateEntry("opensearch-host", cert);
    } catch (final Exception e) {
      final String message =
          "Could not load configured server certificate for the secured OpenSearch Connection!";
      throw new OperateRuntimeException(message, e);
    }
  }

  private Certificate loadCertificateFromPath(final String certificatePath)
      throws IOException, CertificateException {
    final Certificate cert;
    try (final BufferedInputStream bis =
        new BufferedInputStream(new FileInputStream(certificatePath))) {
      final CertificateFactory cf = CertificateFactory.getInstance("X.509");

      if (bis.available() > 0) {
        cert = cf.generateCertificate(bis);
        LOGGER.debug("Found certificate: {}", cert);
      } else {
        throw new OperateRuntimeException(
            "Could not load certificate from file, file is empty. File: " + certificatePath);
      }
    }
    return cert;
  }

  protected HttpAsyncClientBuilder configureHttpClient(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final OpensearchProperties osConfig) {
    setupAuthentication(httpAsyncClientBuilder, osConfig);

    LOGGER.trace("Attempt to load interceptor plugins");
    if (osConfig.getInterceptorPlugins() != null) {
      loadInterceptorPlugins(httpAsyncClientBuilder, osConfig);
    }

    if (osConfig.getSsl() != null) {
      setupSSLContext(httpAsyncClientBuilder, osConfig.getSsl());
    }
    return httpAsyncClientBuilder;
  }

  private void loadInterceptorPlugins(
      final HttpAsyncClientBuilder httpAsyncClientBuilder, final OpensearchProperties osConfig) {
    LOGGER.trace("Plugins detected to be not empty {}", osConfig.getInterceptorPlugins());

    final var interceptors = osConfig.getInterceptorPlugins();
    interceptors.forEach(
        (id, interceptor) -> {
          LOGGER.trace("Attempting to register {}", interceptor.getId());
          try {
            // WARNING! Due to the nature of interceptors, by the moment they (interceptors)
            // are executed, the below class loader will close the JAR file hence
            // to avoid NoClassDefFoundError we must not close this class loader.
            final var classLoader =
                ExternalJarClassLoader.ofPath(Paths.get(interceptor.getJarPath()));

            final var pluginClass = classLoader.loadClass(interceptor.getClassName());
            final var plugin = ReflectUtil.newInstance(pluginClass);

            if (plugin instanceof final DatabaseCustomHeaderSupplier dchs) {
              LOGGER.trace(
                  "Plugin {} appears to be a DB Header Provider. Registering with interceptor",
                  interceptor.getId());
              httpAsyncClientBuilder.addRequestInterceptorLast(
                  (httpRequest, entityDetails, httpContext) -> {
                    final var customHeader =
                        ThreadContextUtil.supplyWithClassLoader(
                            dchs::getElasticsearchCustomHeader, classLoader);
                    httpRequest.addHeader(customHeader.key(), customHeader.value());
                  });
            } else {
              throw new RuntimeException(
                  "Unknown type of interceptor plugin or wrong class specified");
            }
          } catch (final Exception e) {
            throw new RuntimeException("Failed to load interceptor plugin due to exception", e);
          }
        });
  }

  private RequestConfig.Builder setTimeouts(
      final RequestConfig.Builder builder, final OpensearchProperties os) {
    if (os.getSocketTimeout() != null) {
      // builder.setSocketTimeout(os.getSocketTimeout());
      builder.setResponseTimeout(Timeout.ofMilliseconds(os.getSocketTimeout()));
    }
    if (os.getConnectTimeout() != null) {
      builder.setConnectTimeout(Timeout.ofMilliseconds(os.getConnectTimeout()));
    }
    return builder;
  }

  public boolean checkHealth(final OpenSearchClient osClient) {
    final OpensearchProperties osConfig = operateProperties.getOpensearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final HealthResponse clusterHealthResponse =
                  osClient.cluster().health(new HealthRequest.Builder().build());
              return clusterHealthResponse.clusterName().equals(osConfig.getClusterName());
            });
  }

  public boolean checkHealth(final OpenSearchAsyncClient osAsyncClient) {
    final OpensearchProperties osConfig = operateProperties.getOpensearch();
    final RetryPolicy<Boolean> retryPolicy = getConnectionRetryPolicy(osConfig);
    return Failsafe.with(retryPolicy)
        .get(
            () -> {
              final CompletableFuture<HealthResponse> clusterHealthResponse =
                  osAsyncClient.cluster().health(new HealthRequest.Builder().build());
              clusterHealthResponse.whenComplete(
                  (response, e) -> {
                    if (e != null) {
                      LOGGER.error(String.format("Error checking async health %", e.getMessage()));
                    } else {
                      LOGGER.debug("Succesfully returned checkHealth");
                    }
                  });
              return clusterHealthResponse.get().clusterName().equals(osConfig.getClusterName());
            });
  }

  private RetryPolicy<Boolean> getConnectionRetryPolicy(final OpensearchProperties osConfig) {
    final String logMessage = String.format("connect to OpenSearch at %s", osConfig.getUrl());
    return new RetryPolicy<Boolean>()
        .handle(IOException.class, ElasticsearchException.class)
        .withDelay(Duration.ofSeconds(3))
        .withMaxAttempts(50)
        .onRetry(
            e ->
                LOGGER.info(
                    "Retrying #{} {} due to {}",
                    e.getAttemptCount(),
                    logMessage,
                    e.getLastFailure()))
        .onAbort(e -> LOGGER.error("Abort {} by {}", logMessage, e.getFailure()))
        .onRetriesExceeded(
            e -> LOGGER.error("Retries {} exceeded for {}", e.getAttemptCount(), logMessage));
  }
}
