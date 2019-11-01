package app.cash.backfila.client

import javax.inject.Inject
import javax.inject.Singleton
import misk.exceptions.BadRequestException

@Singleton
class ConnectorProvider @Inject constructor(
  @ForConnectors private val connectors: Map<String, BackfilaClientServiceClientProvider>
) {
  fun clientProvider(connectorType: String) =
      connectors[connectorType] ?: throw BadRequestException(
          "Client has unknown connector type: `$connectorType`")
}
