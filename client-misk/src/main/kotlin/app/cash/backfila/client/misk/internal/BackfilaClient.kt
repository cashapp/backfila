package app.cash.backfila.client.misk.internal

import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse

internal interface BackfilaClient {
  fun configureService(request: ConfigureServiceRequest): ConfigureServiceResponse
}
