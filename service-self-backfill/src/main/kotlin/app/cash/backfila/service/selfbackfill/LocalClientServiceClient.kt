package app.cash.backfila.service.selfbackfill

import app.cash.backfila.client.BackfilaClientServiceClient
import app.cash.backfila.client.spi.BackfilaClientServiceHandler
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import javax.inject.Inject

internal class LocalClientServiceClient @Inject internal constructor(
  private val backfilaClientServiceHandler: BackfilaClientServiceHandler,
) : BackfilaClientServiceClient {
  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    return backfilaClientServiceHandler.prepareBackfill(request)
  }

  override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    return backfilaClientServiceHandler.getNextBatchRange(request)
  }

  override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    return backfilaClientServiceHandler.runBatch(request)
  }
}
