package app.cash.backfila.client.spi

import app.cash.backfila.client.Backfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse

/**
 * Backends provide these operators that are invoked to run the underlying backfill logic.
 */
interface BackfillOperator {
  val backfill: Backfill
  fun name(): String
  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse
  fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse
  fun runBatch(request: RunBatchRequest): RunBatchResponse
}
