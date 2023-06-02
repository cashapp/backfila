package app.cash.backfila.client.s3.internal

import app.cash.backfila.client.s3.S3DatasourceBackfill
import app.cash.backfila.client.s3.shim.S3Service
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Stopwatch
import java.util.concurrent.TimeUnit
import okio.ByteString.Companion.encodeUtf8

class S3DatasourceBackfillOperator<R : Any, P : Any>(
  override val backfill: S3DatasourceBackfill<R, P>,
  private val parametersOperator: BackfilaParametersOperator<P>,
  private val s3Service: S3Service,
) : BackfillOperator {

  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config = parametersOperator.constructBackfillConfig(request)
    backfill.validate(config)

    require(request.range?.start == null && request.range?.end == null) {
      // We could think about supporting this later by making the range mean a byte seek into all S3 files.
      // This would mean we would need to support some kind of seek forward or seek back for record start.
      // That or perhaps we only support it for single file?
      // In any case, we are not implementing this now.
      "Range is currently unsupported for S3 Backfils"
    }

    val pathPrefix = backfill.getPrefix(config)
    val fileKeys = s3Service.listFiles(backfill.getBucket(config), pathPrefix)

    // Check that this backfill will actually process something
    require(fileKeys.isNotEmpty()) {
      "No files found for bucket:${backfill.getBucket(config)} prefix:$pathPrefix. At least one file must exist."
    }
    require(fileKeys.size <= 100) {
      "Listing files matching the prefix contains ${fileKeys.size} which is more than 100 files. " +
        "Check your prefix. First 3 file keys ${fileKeys.slice(0..2)}"
    }

    // The path after the prefix must be no more than 300 bytes.
    val postfixes = fileKeys.map { it.removePrefix(pathPrefix) }
    val invalidPostfixes = postfixes.filter { it.encodeUtf8().size > 300 }
    require(invalidPostfixes.isEmpty()) {
      "Found invalid postfixes: $invalidPostfixes"
    }

    val partitions = postfixes.map {
      PrepareBackfillResponse.Partition.Builder()
        .partition_name(it)
        .backfill_range(
          KeyRange.Builder()
            .start((0L).toString().encodeUtf8())
            .build(),
        )
        .build()
    }
    return PrepareBackfillResponse.Builder()
      .partitions(partitions)
      .build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    val config = parametersOperator.constructBackfillConfig(request).prepareConfig()
    val pathPrefix = backfill.getPrefix(config)

    val batchSize = request.batch_size.toInt()
    val previousEndKey = request.previous_end_key?.utf8()?.toLong() ?: 0L
    val fileSize = s3Service.getFileSize(
      backfill.getBucket(config),
      pathPrefix + request.partition_name,
    )

    if (previousEndKey == fileSize) {
      // Either the file is empty or we have reached the end of the file.
      return GetNextBatchRangeResponse.Builder().batches(
        listOf(),
      ).build()
    }

    // When precomputing all we are trying to do is figure out how big the file is.
    if (request.precomputing == true) {
      require(previousEndKey == 0L) {
        "The file size changed between batch calculations."
      }
      return GetNextBatchRangeResponse.Builder().batches(
        listOf(
          Batch.Builder()
            .batch_range(
              KeyRange(
                (0L).toString().encodeUtf8(),
                fileSize.toString().encodeUtf8(),
              ),
            )
            .matching_record_count(fileSize)
            .scanned_record_count(fileSize)
            .build(),
        ),
      ).build()
    }

    val bytesToScan = Math.min(previousEndKey + backfill.scanByteStrategy.bytesToScan(), fileSize)
    val fileStream = s3Service.getFileChunkSource(
        backfill.getBucket(config),
        pathPrefix + request.partition_name,
        previousEndKey,
        bytesToScan,
    )

    val result = fileStream.use {
      val recordBytes = mutableListOf<Long>()
      val stopwatch = Stopwatch.createStarted()
      while (!fileStream.exhausted() && // There is file to stream.
          (
              request.compute_time_limit_ms == null || // Either there is no limit or we are within our timeframe.
              stopwatch.elapsed(TimeUnit.MILLISECONDS) <= request.compute_time_limit_ms
          )
      ) {
        val peekSource = fileStream.peek()
        val bytes = backfill.recordStrategy.calculateNextRecordBytes(peekSource)
        require(bytes > 0) { "Failed to consume any streamed bytes for ${request.partition_name}" }
        recordBytes += bytes
        fileStream.skip(bytes)
      }

      var offset = previousEndKey
      val batches = mutableListOf<Batch>()
      recordBytes.chunked(batchSize).map { it.sum() }.forEach { size ->
        batches += Batch.Builder()
            .batch_range(
                KeyRange(
                    (offset).toString().encodeUtf8(),
                    (offset + size).toString().encodeUtf8(),
                ),
            )
            .matching_record_count(size)
            .scanned_record_count(size)
            .build()
        offset += size
      }

      // Provide feedback on how effective the strategy was.
      backfill.scanByteStrategy.recordResult(request, batches, stopwatch)

      batches
    }

    return GetNextBatchRangeResponse.Builder()
      .batches(result)
      .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val config = parametersOperator.constructBackfillConfig(request)
    val pathPrefix = backfill.getPrefix(config.prepareConfig())
    val batchRange = request.batch_range.decode()
    requireNotNull(batchRange.end) { "Batch was created without a range end." }

    val byteString = s3Service.getFileChunk(
      backfill.getBucket(config.prepareConfig()),
      pathPrefix + request.partition_name,
      batchRange.start,
      batchRange.end,
    )

    val batch = backfill.recordStrategy.bytesToRecords(byteString)

    backfill.runBatch(batch, config)

    return RunBatchResponse.Builder()
      .build()
  }

  data class DecodedRange(
    val start: Long,
    val end: Long,
  )
  private fun KeyRange.decode(): DecodedRange {
    val start = this.start.utf8().toLong()
    val end = this.end.utf8().toLong()
    return DecodedRange(start, end)
  }
}
