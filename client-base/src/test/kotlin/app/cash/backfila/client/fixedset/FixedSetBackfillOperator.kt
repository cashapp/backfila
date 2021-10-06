package app.cash.backfila.client.fixedset

import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import okio.ByteString.Companion.encodeUtf8

class FixedSetBackfillOperator<Param : Any>(
  override val backfill: FixedSetBackfill<Param>,
  private val datastore: FixedSetDatastore,
  private val parametersOperator: BackfilaParametersOperator<Param>
) : BackfillOperator {
  override fun name() = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    backfill.checkBackfillConfig(
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    )

    val partitions = mutableListOf<PrepareBackfillResponse.Partition>()

    for (fixedPartition in datastore.dataByInstance) {
      partitions.add(
        PrepareBackfillResponse.Partition.Builder()
          .partition_name(fixedPartition.key)
          .backfill_range(makeKeyRange(0, fixedPartition.value.size - 1))
          .build()
      )
    }

    return PrepareBackfillResponse.Builder()
      .partitions(partitions)
      .build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    backfill.checkBackfillConfig(
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    )

    val partition = datastore.dataByInstance[request.partition_name] ?: error("Invalid partition name")
    val previousEndKey: Int = request.previous_end_key?.utf8()?.toInt() ?: -1
    if (previousEndKey == partition.size - 1) {
      return GetNextBatchRangeResponse.Builder()
        .build()
    } else {
      val end = minOf(previousEndKey + request.batch_size.toInt(), partition.size - 1)
      val start = previousEndKey + 1
      val batch = GetNextBatchRangeResponse.Batch.Builder()
        .batch_range(makeKeyRange(start, end))
        .matching_record_count((end - start).toLong())
        .scanned_record_count((end - start).toLong())
        .build()
      return GetNextBatchRangeResponse.Builder()
        .batches(listOf(batch))
        .build()
    }
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    backfill.checkBackfillConfig(
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    )
    val partition = datastore.dataByInstance[request.partition_name]!!
    val start = request.batch_range.start.utf8().toInt()
    val end = request.batch_range.end.utf8().toInt()
    for (i in start..end) {
      backfill.runOne(partition[i])
    }

    return RunBatchResponse.Builder().build() // Return empty 200
  }

  private fun makeKeyRange(
    start: Int,
    end: Int
  ): KeyRange? {
    return KeyRange.Builder()
      .start(start.toString().encodeUtf8())
      .end(end.toString().encodeUtf8())
      .build()
  }
}
