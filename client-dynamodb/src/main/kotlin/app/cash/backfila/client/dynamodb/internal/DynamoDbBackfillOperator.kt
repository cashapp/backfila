package app.cash.backfila.client.dynamodb.internal

import app.cash.backfila.client.dynamodb.DynamoDbBackfill
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.google.common.base.Stopwatch
import java.time.Duration

@Deprecated(
  message = "AWS V1 SDK is deprecated, use the `client-dynamodb-v2` client instead.",
)
class DynamoDbBackfillOperator<I : Any, P : Any>(
  val dynamoDb: DynamoDBMapper,
  override val backfill: DynamoDbBackfill<I, P>,
  val parametersOperator: BackfilaParametersOperator<P>,
  val keyRangeCodec: DynamoDbKeyRangeCodec,
) : BackfillOperator {
  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config = parametersOperator.constructBackfillConfig(request)
    backfill.validate(config)

    require(
      request.range == null ||
        (request.range.start == null && request.range.end == null),
    ) { "Range is not supported for this Dynamo Backfila client" }

    val tableMapper = dynamoDb.newTableMapper<I, Any, Any>(backfill.itemType.java)
    val tableDescription = tableMapper.describeTable()
    if (backfill.mustHaveProvisionedBillingMode()) {
      // It's odd but a null billingModeSummary implies "PROVISIONED"
      require(
        tableDescription.billingModeSummary == null ||
          tableDescription.billingModeSummary.billingMode == "PROVISIONED",
      ) {
        "Trying to prepare a backfill on a Dynamo table named ${tableDescription.tableName} " +
          "with a billing mode that is not PROVISIONED, it is " +
          "${tableDescription.billingModeSummary.billingMode}. This can get very expensive. " +
          "Please provision your dynamo capacity for this table and try again."
      }
    }

    val partitionCount = backfill.partitionCount(config)
    val desiredSegmentCount = (tableDescription.itemCount / 100L).coerceIn(partitionCount.toLong(), 524288L)
    val defaultSegmentCount = desiredSegmentCount.takeHighestOneBit().toInt() // closest power of 2
    val segmentCount = backfill.fixedSegmentCount(config) ?: defaultSegmentCount
    require(
      partitionCount in 1..segmentCount &&
        Integer.bitCount(partitionCount) == 1 &&
        Integer.bitCount(segmentCount) == 1,
    ) {
      "partitionCount and segmentCount must be positive powers of 2, and segmentCount must be " +
        "greater than partitionCount (partitionCount=$partitionCount, segmentCount=$segmentCount)"
    }
    val segmentsPerPartition = segmentCount / partitionCount

    val partitions = mutableListOf<PrepareBackfillResponse.Partition>()
    for (i in 0 until partitionCount) {
      val segmentStartInclusive = i * segmentsPerPartition
      val segmentEndExclusive = (i + 1) * segmentsPerPartition
      partitions += PrepareBackfillResponse.Partition.Builder()
        .partition_name("$i of $partitionCount")
        .backfill_range(keyRangeCodec.encodeKeyRange(segmentStartInclusive, segmentEndExclusive, segmentCount))
        .build()
    }

    return PrepareBackfillResponse.Builder()
      .partitions(partitions)
      .build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    var (start, end, count) = keyRangeCodec.decodeKeyRange(request.backfill_range)

    // If this isn't the first batch range, start where we last left off.
    if (request.previous_end_key != null) {
      val segmentData = keyRangeCodec.decodeSegment(request.previous_end_key)
      require(segmentData.count == count) // Segment count cannot change.
      require(segmentData.lastEvaluatedKey == null) // No partial batches until a batch is running.
      start = segmentData.offset
    }

    val batches = mutableListOf<GetNextBatchRangeResponse.Batch>()
    for (i in start until minOf(start + 1, end)) {
      batches += GetNextBatchRangeResponse.Batch.Builder()
        .batch_range(keyRangeCodec.encodeKeyRange(i, i + 1, count))
        // In order to keep costs down we do not perform any queries here for an accurate count. We
        // may consider adding that feature at a later date.
        .matching_record_count(1L)
        .scanned_record_count(1L)
        .build()
    }

    return GetNextBatchRangeResponse.Builder()
      .batches(batches)
      .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val keyRange = keyRangeCodec.decodeKeyRange(request.batch_range)
    require(keyRange.end == keyRange.start + 1)

    val config = parametersOperator.constructBackfillConfig(request)

    var lastEvaluatedKey: Map<String, AttributeValue>? = keyRange.lastEvaluatedKey

    val stopwatch = Stopwatch.createStarted()
    do {
      val scanRequest = DynamoDBScanExpression().apply {
        segment = keyRange.start
        totalSegments = keyRange.count
        limit = request.batch_size.toInt()
        if (lastEvaluatedKey != null) {
          exclusiveStartKey = lastEvaluatedKey
        }
        this.filterExpression = backfill.filterExpression(config)
        this.expressionAttributeValues = backfill.expressionAttributeValues(config)
        this.expressionAttributeNames = backfill.expressionAttributeNames(config)
        this.indexName = backfill.indexName(config)
      }
      val result = dynamoDb.scanPage(backfill.itemType.java, scanRequest)
      backfill.runBatch(result.results, config)
      lastEvaluatedKey = result.lastEvaluatedKey
      if (stopwatch.elapsed() > Duration.ofMillis(1_000L)) {
        break
      }
    } while (lastEvaluatedKey != null)

    return RunBatchResponse.Builder()
      .remaining_batch_range(lastEvaluatedKey?.toKeyRange(keyRange))
      .build()
  }

  private fun Map<String, AttributeValue>.toKeyRange(originalRange: DynamoDbKeyRange): KeyRange {
    require(originalRange.start + 1 == originalRange.end)
    return keyRangeCodec.encodeKeyRange(originalRange.start, originalRange.end, originalRange.count, this)
  }
}
