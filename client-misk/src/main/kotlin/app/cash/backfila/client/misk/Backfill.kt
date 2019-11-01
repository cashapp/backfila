package app.cash.backfila.client.misk

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse

interface Backfill {
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse

  fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse

  fun runBatch(request: RunBatchRequest): RunBatchResponse
}
