package app.cash.backfila.client

import java.time.Duration
import misk.client.HttpClientEndpointConfig

val DEFAULT_HTTP_CLIENT_ENDPOINT_CONFIG = HttpClientEndpointConfig(
    // Allow RunBatch requests to take a long time.
    readTimeout = Duration.ofSeconds(30L),
    // Use a large number, we limit parallel calls ourselves.
    maxRequests = 1000,
    maxRequestsPerHost = 1000
)

interface BackfilaClientServiceClientProvider {
  fun validateExtraData(connectorExtraData: String?)
  fun clientFor(serviceName: String, connectorExtraData: String?): BackfilaClientServiceClient
}
