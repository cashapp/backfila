package app.cash.backfila.client.dynamodbv2.internal

import app.cash.backfila.client.dynamodbv2.DynamoDbBackfill
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.google.common.base.Stopwatch
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import software.amazon.awssdk.services.dynamodb.model.BillingMode
import software.amazon.awssdk.services.dynamodb.model.ScanRequest
import java.time.Duration

class DynamoDbBackfillOperator<I : Any, P : Any>(
  private val dynamoDbClient: DynamoDbClient,
  override val backfill: DynamoDbBackfill<I, P>,
  private val parametersOperator: BackfilaParametersOperator<P>,
  private val keyRangeCodec: DynamoDbKeyRangeCodec,
) : BackfillOperator {
  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config =
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    backfill.validate(config)

    if (backfill.mustHaveProvisionedBillingMode()) {
      var tableResponse = dynamoDbClient.describeTable {
        it.tableName(backfill.dynamoDbTable.tableName())
      }
      var billingModeSummary = tableResponse.table().billingModeSummary()
      // It's odd but a null billingModeSummary implies "PROVISIONED"
      require(
        billingModeSummary == null ||
          billingModeSummary.billingMode() == BillingMode.PROVISIONED
      ) {
        "Trying to prepare a backfill on a Dynamo table named ${backfill.dynamoDbTable.tableName()} " +
          "with a billing mode that is not PROVISIONED, it is " +
          "${backfill.dynamoDbTable.tableName()}. This can get very expensive. " +
          "Please provision your dynamo capacity for this table and try again."
      }
    }

    // TODO(mikepaw): dynamically select the segment count by probing DynamoDB.
    // The maximum number of segments is 1,000,000 so we are currently setting it to the largest power 
    // of 2 less than this value.
    val segmentCount = backfill.fixedSegmentCount(config) ?: 524288
    val partitionCount = backfill.partitionCount(config)
    require(
      partitionCount in 1..segmentCount &&
        Integer.bitCount(partitionCount) == 1 &&
        Integer.bitCount(segmentCount) == 1
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
        .backfill_range(
          keyRangeCodec.encodeKeyRange(
            segmentStartInclusive,
            segmentEndExclusive,
            segmentCount
          )
        )
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
        // TODO(mikepaw) calculate counts accurately when requested.
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

    val config =
      parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)

    var lastEvaluatedKey: Map<String, AttributeValue>? = keyRange.lastEvaluatedKey

    val stopwatch = Stopwatch.createStarted()
    do {
      val scanRequest = ScanRequest.builder()
        .tableName(backfill.dynamoDbTable.tableName())
        .segment(keyRange.start)
        .totalSegments(keyRange.count)
        .limit(request.batch_size.toInt())
        .let {
          if (lastEvaluatedKey != null && lastEvaluatedKey!!.isNotEmpty()) {
            it.exclusiveStartKey(lastEvaluatedKey)
          } else {
            it
          }
        }
        .filterExpression(backfill.filterExpression(config))
        .expressionAttributeValues(backfill.expressionAttributeValues(config))
        .build()

      val result = dynamoDbClient.scan(scanRequest)

      backfill.runBatch(
        result.items().map {
          backfill.dynamoDbTable.tableSchema().mapToItem(it)
        },
        config
      )
      lastEvaluatedKey = result.lastEvaluatedKey()
      if (stopwatch.elapsed() > Duration.ofMillis(1_000L)) {
        break
      }
    } while (lastEvaluatedKey != null && lastEvaluatedKey.isNotEmpty())

    return RunBatchResponse.Builder()
      .let {
        if (lastEvaluatedKey != null && lastEvaluatedKey.isNotEmpty()) {
          it.remaining_batch_range(lastEvaluatedKey.toKeyRange(keyRange))
        } else {
          it.remaining_batch_range(null)
        }
      }
      .build()
  }

  private fun Map<String, AttributeValue>.toKeyRange(originalRange: DynamoDbKeyRange): KeyRange {
    require(originalRange.start + 1 == originalRange.end)
    return keyRangeCodec.encodeKeyRange(
      originalRange.start,
      originalRange.end,
      originalRange.count,
      this
    )
  }
}
