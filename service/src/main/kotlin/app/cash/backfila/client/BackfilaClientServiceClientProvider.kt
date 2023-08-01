package app.cash.backfila.client

import com.google.inject.Provides
import java.time.Duration
import misk.client.HttpClientConfig
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule

val DEFAULT_HTTP_CLIENTS_CONFIG = HttpClientsConfig(
  hostConfigs = linkedMapOf(
    ".*" to HttpClientConfig(
      // Allow RunBatch requests to take a long time.
      readTimeout = Duration.ofSeconds(30L),
      // Use a large number, we limit parallel calls ourselves.
      maxRequests = 1000,
      maxRequestsPerHost = 1000,
    ),
  ),
)

class BackfilaDefaultEndpointConfigModule() : KAbstractModule() {
  @Provides
  fun httpClientsConfigDefault() = DEFAULT_HTTP_CLIENTS_CONFIG
}

interface BackfilaClientServiceClientProvider {
  fun validateExtraData(connectorExtraData: String?)
  fun clientFor(serviceName: String, connectorExtraData: String?, perRunOverrideData: PerRunOverrideData?): BackfilaClientServiceClient
}
