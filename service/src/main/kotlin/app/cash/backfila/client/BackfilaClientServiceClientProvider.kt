package app.cash.backfila.client

import com.google.inject.Provides
import misk.client.HttpClientConfig
import java.time.Duration
import javax.inject.Named
import misk.client.HttpClientsConfig
import misk.inject.KAbstractModule
import java.net.URL

val DEFAULT_HTTP_CLIENTS_CONFIG = HttpClientsConfig(
  hostConfigs = linkedMapOf(
      ".*" to HttpClientConfig(
          // Allow RunBatch requests to take a long time.
          readTimeout = Duration.ofSeconds(30L),
          // Use a large number, we limit parallel calls ourselves.
          maxRequests = 1000,
          maxRequestsPerHost = 1000
      )
  )
)

class BackfilaDefaultEndpointConfigModule() : KAbstractModule() {
  @Provides
  fun httpClientsConfigDefault() = DEFAULT_HTTP_CLIENTS_CONFIG

  @Provides
  fun noConnectorTypeToUrlConverter() = object : ConnectorTypeToUrlConverter {
    override fun urlForType(connectorType: String, connectorExtraData: String?): String {
      error("Tried to convert connector to a url when no converter is setup. " +
          "type: $connectorType , extraData: $connectorExtraData ")
    }
  }
}

interface ConnectorTypeToUrlConverter {
  fun urlForType(connectorType: String, connectorExtraData: String?): String
}
