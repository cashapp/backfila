package app.cash.backfila.client.misk.dynamodb.internal

import app.cash.backfila.client.misk.dynamodb.DynamoDbBackfill
import app.cash.backfila.client.misk.spi.BackfilaParametersOperator
import app.cash.backfila.client.misk.spi.BackfillOperator
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue

class DynamoDbBackfillOperator<I : Any, P : Any>(
  val dynamoDb: DynamoDBMapper,
  override val backfill: DynamoDbBackfill<I, P>,
  val parametersOperator: BackfilaParametersOperator<P>
) : BackfillOperator {
  override fun name(): String = backfill.javaClass.toString()

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val config =
        parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)
    backfill.validate(config)

    // TODO(mikepaw): dynamically select the segment count by probing DynamoDB.
    val segmentCount = backfill.fixedSegmentCount(config) ?: 2048
    val partitionCount = backfill.partitionCount(config)
    val segmentsPerPartition = segmentCount / partitionCount

    val partitions = mutableListOf<PrepareBackfillResponse.Partition>()
    for (i in 0 until partitionCount) {
      val segmentStartInclusive = i * segmentsPerPartition
      val segmentEndExclusive = (i + 1) * segmentsPerPartition
      partitions += PrepareBackfillResponse.Partition.Builder()
          .partition_name("$i of $partitionCount")
          .backfill_range(encodeKeyRange(segmentStartInclusive, segmentEndExclusive, segmentCount))
          .build()
    }

    return PrepareBackfillResponse.Builder()
        .partitions(partitions)
        .build()
  }

  override fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    var (start, end, count) = decodeKeyRange(request.backfill_range)

    // If this isn't the first batch range, start where we last left off.
    if (request.previous_end_key != null) {
      start = decodeSegment(request.previous_end_key).first
    }

    val batches = mutableListOf<GetNextBatchRangeResponse.Batch>()
    for (i in start until minOf(start + 1, end)) {
      batches += GetNextBatchRangeResponse.Batch.Builder()
          .batch_range(encodeKeyRange(i, i + 1, count))
          .matching_record_count(100L)
          .scanned_record_count(100L)
          .build()
    }

    return GetNextBatchRangeResponse.Builder()
        .batches(batches)
        .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val (start, end, count) = decodeKeyRange(request.batch_range)
    require(end == start + 1)

    val config =
        parametersOperator.constructBackfillConfig(request.parameters, request.dry_run)

    var lastEvaluatedKey: Map<String, AttributeValue>? = null
    do {
      val scanRequest = DynamoDBScanExpression().apply {
        segment = start
        totalSegments = count
        limit = 4
        if (lastEvaluatedKey != null) {
          exclusiveStartKey = lastEvaluatedKey
        }
        this.filterExpression = backfill.filterExpression(config)
        this.expressionAttributeValues = backfill.expressionAttributeValues(config)
      }
      val result = dynamoDb.scanPage(backfill.itemType.java, scanRequest)
      backfill.runBatch(result.results, config)
      lastEvaluatedKey = result.lastEvaluatedKey
    } while (lastEvaluatedKey != null)

    return RunBatchResponse.Builder()
        .build()
  }
}
