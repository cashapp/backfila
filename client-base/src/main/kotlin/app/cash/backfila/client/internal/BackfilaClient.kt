package app.cash.backfila.client.internal

import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse

interface BackfilaClient {
  fun configureService(request: ConfigureServiceRequest): ConfigureServiceResponse

  fun createAndStartBackfill(request: CreateAndStartBackfillRequest): CreateAndStartBackfillResponse

  fun checkBackfillStatus(request: CheckBackfillStatusRequest): CheckBackfillStatusResponse
}
