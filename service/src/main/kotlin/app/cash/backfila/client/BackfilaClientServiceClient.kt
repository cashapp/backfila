package app.cash.backfila.client

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse

interface BackfilaClientServiceClient {
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse

  suspend fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse

  suspend fun runBatch(request: RunBatchRequest): RunBatchResponse

  /**
   * Gives us a way to probe for connection information when something fails to help with debugging.
   */
  fun connectionLogData(): String
}
