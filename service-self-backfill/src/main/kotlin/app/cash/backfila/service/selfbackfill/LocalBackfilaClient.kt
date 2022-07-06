package app.cash.backfila.service.selfbackfill

import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse
import javax.inject.Inject
import misk.MiskCaller
import misk.inject.keyOf
import misk.scope.ActionScope

internal class LocalBackfilaClient @Inject constructor(
  private val configureServiceAction: ConfigureServiceAction,
  private val actionScope: ActionScope
) : BackfilaClient {
  override fun configureService(request: ConfigureServiceRequest): ConfigureServiceResponse {
    actionScope.enter(
      mapOf(
        keyOf<MiskCaller>() to MiskCaller(service = "backfila")
      )
    ).use {
      return configureServiceAction.configureService(request)
    }
  }

  override fun createAndStartBackfill(request: CreateAndStartBackfillRequest): CreateAndStartBackfillResponse {
    // We don't need this
    TODO("Not yet implemented")
  }

  override fun checkBackfillStatus(request: CheckBackfillStatusRequest): CheckBackfillStatusResponse {
    // We don't need this
    TODO("Not yet implemented")
  }

  override val throwOnStartup: Boolean
    get() = true
}
