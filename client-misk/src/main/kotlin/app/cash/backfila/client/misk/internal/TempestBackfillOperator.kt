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

interface DynamoBackfill<Pkey : Any, Param : Any> {

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: BackfillConfig<Param>) {}

  /**
   * Called for each batch of matching records.
   * Override in a backfill to process all records in a batch.
   */
  open fun runBatch(pkeys: List<Pkey>, config: BackfillConfig<Param>) {
    pkeys.forEach { runOne(it, config) }
  }

  /**
   * Called for each matching record.
   * Override in a backfill to process one record at a time.
   */
  open fun runOne(pkey: Pkey, config: BackfillConfig<Param>) {
  }
}
