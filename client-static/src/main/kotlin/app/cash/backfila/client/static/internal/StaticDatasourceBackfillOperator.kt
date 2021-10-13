package app.cash.backfila.client.static.internal

import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.static.StaticDatasourceBackfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Preconditions.checkArgument
import okio.ByteString.Companion.encodeUtf8

class StaticDatasourceBackfillOperator<I : Any, P : Any>(
  override val backfill: StaticDatasourceBackfill<I, P>,
  val parametersOperator: BackfilaParametersOperator<P>
) : BackfillOperator {

  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config =
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    backfill.validate(config)

    val start = request.range?.start?.utf8()?.let {
      it.toIntOrNull() ?: error("Start of range must be a number")
    } ?: 0
    val end = request.range?.end?.utf8()?.let {
      it.toIntOrNull() ?: error("End of range must be a number")
    } ?: backfill.staticDatasource.size

    // Sanity check that this backfill will actually process something
    require(start >= 0 && end >= 0) {
      "Start and end must be positive integers, start: $start end: $end"
    }
    require(start <= end) {
      "Start must be less than or equal to end, start: $start end: $end"
    }
    require(start <= backfill.staticDatasource.size) {
      "Start is greater than the static datasource size, start: $start size: ${backfill.staticDatasource.size}"
    }

    val onlyPartition = PrepareBackfillResponse.Partition.Builder()
      .partition_name(PARTITION)
      .backfill_range(
        KeyRange.Builder()
          .start(start.toString().encodeUtf8())
          .end(end.toString().encodeUtf8())
          .build()
      )
      .build()

    return PrepareBackfillResponse.Builder()
      .partitions(listOf(onlyPartition))
      .build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    checkArgument(request.partition_name == PARTITION, "Attempting to get batch for unknown partition ${request.partition_name}")
    val batchSize = request.batch_size.toInt()
    val scanSize = request.scan_size.toInt()

    val backfillRange = request.backfill_range.decode()

    val batchingStart = request.previous_end_key?.utf8()?.toInt() ?: backfillRange.start

    val batches = mutableListOf<GetNextBatchRangeResponse.Batch>()
    for (batchStart in batchingStart until minOf(batchingStart + scanSize, backfillRange.end) step batchSize) {
      val batchEnd = minOf(batchStart + batchSize, backfillRange.end)
      batches += GetNextBatchRangeResponse.Batch.Builder()
        .batch_range(
          KeyRange.Builder()
            .start(batchStart.toString().encodeUtf8())
            .end(batchEnd.toString().encodeUtf8())
            .build()
        )
        .matching_record_count((batchEnd - batchStart).toLong())
        .scanned_record_count((batchEnd - batchStart).toLong())
        .build()
    }

    return GetNextBatchRangeResponse.Builder()
      .batches(batches)
      .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    checkArgument(request.partition_name == PARTITION, "Attempting to get batch for unknown partition ${request.partition_name}")
    val batchRange = request.batch_range.decode()
    val config = parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)

    val batch = backfill.staticDatasource.subList(batchRange.start, batchRange.end)

    backfill.runBatch(batch, config)

    return RunBatchResponse.Builder()
      .build()
  }

  data class DecodedRange(
    val start: Int,
    val end: Int
  )
  private fun KeyRange.decode(): DecodedRange {
    val start = this.start.utf8().toInt()
    val end = this.end.utf8().toInt()
    return DecodedRange(start, end)
  }

  companion object {
    private const val PARTITION = "only"
  }
}
