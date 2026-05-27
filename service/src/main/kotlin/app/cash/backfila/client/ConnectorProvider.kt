package app.cash.backfila.client

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.exceptions.BadRequestException

@Singleton
class ConnectorProvider @Inject constructor(
  @ForConnectors private val connectors: Map<String, BackfilaCallbackConnectorProvider>,
) {
  fun clientProvider(connectorType: String) =
    connectors[connectorType] ?: throw BadRequestException(
      "Client has unknown connector type: `$connectorType`",
    )
}
