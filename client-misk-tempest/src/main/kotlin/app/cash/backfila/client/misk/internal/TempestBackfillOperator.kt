package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse

class TempestBackfillOperator(
//  val backfill: DynamoBackfill
) : BackfillOperator {

  override fun name() = TODO() // backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    TODO("Not yet implemented")
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    TODO("Not yet implemented")
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    TODO("Not yet implemented")
  }
}
