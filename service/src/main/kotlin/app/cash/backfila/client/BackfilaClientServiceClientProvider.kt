package app.cash.backfila.client

import com.google.inject.Provides
import java.time.Duration
import javax.inject.Named
import misk.client.HttpClientEndpointConfig
import misk.inject.KAbstractModule

val DEFAULT_HTTP_CLIENT_ENDPOINT_CONFIG = HttpClientEndpointConfig(
    // Allow RunBatch requests to take a long time.
    readTimeout = Duration.ofSeconds(30L),
    // Use a large number, we limit parallel calls ourselves.
    maxRequests = 1000,
    maxRequestsPerHost = 1000
)

class BackfilaDefaultEndpointConfigModule() : KAbstractModule() {
  @Provides @Named(Connectors.HTTP)
  fun httpClientEndpointConfigDefault() = DEFAULT_HTTP_CLIENT_ENDPOINT_CONFIG

  @Provides @Named(Connectors.ENVOY)
  fun envoyClientEndpointConfigDefault() = DEFAULT_HTTP_CLIENT_ENDPOINT_CONFIG
}

interface BackfilaClientServiceClientProvider {
  fun validateExtraData(connectorExtraData: String?)
  fun clientFor(serviceName: String, connectorExtraData: String?): BackfilaClientServiceClient
}
