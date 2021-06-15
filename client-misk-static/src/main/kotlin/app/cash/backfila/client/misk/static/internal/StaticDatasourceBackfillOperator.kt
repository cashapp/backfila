package app.cash.backfila.client.misk.static.internal

import app.cash.backfila.client.misk.spi.BackfilaParametersOperator
import app.cash.backfila.client.misk.spi.BackfillOperator
import app.cash.backfila.client.misk.static.StaticDatasourceBackfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Preconditions.checkArgument
import okio.ByteString.Companion.encodeUtf8
import java.lang.NullPointerException
import misk.exceptions.BadRequestException

class StaticDatasourceBackfillOperator<I : Any, P : Any>(
  override val backfill: StaticDatasourceBackfill<I, P>,
  val parametersOperator: BackfilaParametersOperator<P>
) : BackfillOperator {

  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config =
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    backfill.validate(config)

    val start: Int
    val end: Int
    try {
      start = request.range?.start?.utf8()?.toInt() ?: 0
    } catch (e: NumberFormatException) {
      throw BadRequestException("Start of range must be a number", e)
    }

    try {
      end = request.range?.end?.utf8()?.toInt() ?: backfill.staticDatasource.size
    } catch (e: NumberFormatException) {
      throw BadRequestException("End of range must be a number", e)
    }

    // Sanity check that this backfill will actually process something
    if (start < 0 || end < 0) {
      throw BadRequestException("Start and end must be positive integers, start: $start end: $end")
    }
    if (start > end) {
      throw BadRequestException("Start must be less than end, start: $start end: $end")
    }
    if (start > backfill.staticDatasource.size) {
      throw BadRequestException("Start is greater than the static datasource size, start: $start size: ${backfill.staticDatasource.size}")
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

    val batchingStart: Int
    try {
      batchingStart = request.previous_end_key?.utf8()?.toInt() ?: backfillRange.start
    } catch (e: NumberFormatException) {
      throw BadRequestException("previous_end_key must be a number", e)
    }

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
    val start: Int
    try {
      start = this.start.utf8().toInt()
    } catch (e: NumberFormatException) {
      throw BadRequestException("Start of range must be a number", e)
    } catch (e: NullPointerException) {
      throw BadRequestException("Start of range must not be null", e)
    }

    val end: Int
    try {
      end = this.end.utf8().toInt()
    } catch (e: NumberFormatException) {
      throw BadRequestException("End of range must be a number", e)
    } catch (e: NullPointerException) {
      throw BadRequestException("End of range must not be null", e)
    }
    return DecodedRange(start, end)
  }

  companion object {
    private const val PARTITION = "only"
  }
}
