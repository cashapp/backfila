package app.cash.backfila.client

import misk.exceptions.BadRequestException

class ConnectorProvider constructor(
  private val connectors: Map<String, BackfilaClientServiceClientProvider>
) {
  constructor(vararg connectors: Pair<String, BackfilaClientServiceClientProvider>)
      : this(connectors.toMap())

  fun clientProvider(connectorType: String) =
      connectors[connectorType] ?: throw BadRequestException(
          "Client has unknown connector type: `$connectorType`")
}
