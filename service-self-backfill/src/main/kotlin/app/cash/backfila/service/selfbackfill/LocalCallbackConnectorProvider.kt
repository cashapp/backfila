package app.cash.backfila.service.selfbackfill

import app.cash.backfila.client.BackfilaCallbackConnector
import app.cash.backfila.client.BackfilaCallbackConnectorProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocalCallbackConnectorProvider @Inject internal constructor(
  private val localClientServiceClient: LocalCallbackConnector,
) : BackfilaCallbackConnectorProvider {
  override fun validateExtraData(connectorExtraData: String?) {
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?,
  ): BackfilaCallbackConnector {
    return localClientServiceClient
  }
}
