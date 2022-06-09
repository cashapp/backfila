package app.cash.backfila.service.selfbackfill

import app.cash.backfila.client.BackfilaClientServiceClient
import app.cash.backfila.client.BackfilaClientServiceClientProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocalClientServiceClientProvider @Inject internal constructor(
  private val localClientServiceClient: LocalClientServiceClient,
) : BackfilaClientServiceClientProvider {
  override fun validateExtraData(connectorExtraData: String?) {
  }

  override fun clientFor(
    serviceName: String,
    connectorExtraData: String?
  ): BackfilaClientServiceClient {
    return localClientServiceClient
  }
}
