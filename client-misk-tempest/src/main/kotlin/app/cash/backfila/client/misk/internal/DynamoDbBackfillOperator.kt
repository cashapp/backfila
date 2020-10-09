package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression
import com.amazonaws.services.dynamodbv2.document.TableWriteItems
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ScanRequest
import com.amazonaws.services.dynamodbv2.model.ScanResult
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest
import okio.Buffer
import okio.ByteString

class DynamoDbBackfillOperator<I : Any, P : Any>(
  val dynamoDb: DynamoDBMapper,
  val backfill: DynamoBackfill<I, P>,
  val config: BackfillConfig<P>
) : BackfillOperator {

  override fun name() = backfill.javaClass.toString()

  /*
  NEXT STEPS.

  1. prepareBackfill uses DynamoDbMapper API directly (not Tempest) to figure out how big
     the batches should be. Target range... 64 rows per batch.

     the strategy here could be... pick 7 random slices between 0 and 2^N, take the biggest one
        if it's bigger than 32, then we found our N
        otherwise increment N and repeat

  2.  prepareBackfill returns "8" ranges, each of which is 1/N values... like   this if N is 16 [0..2) of 16 [2..4) of 16
    //  [0..256)
    //  [256..512)
    //  [512..768)
    //  [768..1024)
    //  ...
    //  [768..2048)

  3. When getNextBatchRange batches, sequentually proceed through the range, using the partition count selected
     in prepareBackfill.

     Start off with each batch doing...
     partition [0..1] of 16
       batch 0 is segment 0 of 16
       batch 1 is segment 1 of 16
     partition 1
       batch 0 is segment 2 of 16
       batch 1 is segment 3 of 16

     Optimization divide more evenly but we then have the delete problem.

     VERSION 1 : nextBatchRange always returns just segment info
     VERSION 1 : nextBatchRange always returns THE SAME HASH KEY WE'RE CURRENTLY PROCESSING, plus sort key

     A RunBatchRequest includes:
      - segment information ... 3 of 2048
      - optional last_evaluated_key/exclusive_start_key

      input: 3 of 2048
      output: EITHER 4 of 2048
                 OR 3 of 2048 + stopAt Track_23423
                 OR 3 of 2048 + resumeAt TRACK_23423

      Maybe later version:
      Let RunBatch return after some deadline with an updated RunBatchRequest to pass back to it immediately on how to continue in this case it would be some last_evaluated_key.

   */

  override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val segmentCount = 2048
    val partitionCount = 8
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

  private fun encodeSegment(offset: Int, count: Int): ByteString {
    require(offset in 0..count)
    return Buffer()
        .writeUtf8("v1")
        .writeInt(offset)
        .writeInt(count)
        .readByteString()
  }

  private fun decodeSegment(segment: ByteString): Pair<Int, Int> {
    val buffer = Buffer().write(segment)
    val tag = buffer.readUtf8(2)
    require(tag == "v1")
    val offset = buffer.readInt()
    val count = buffer.readInt()
    return offset to count
  }

  data class DynamoDbKeyRange(val start: Int, val end: Int, val count: Int)

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
          .build()
    }

    return GetNextBatchRangeResponse.Builder()
        .batches(batches)
        .build()
  }

  private fun decodeKeyRange(keyRange: KeyRange): DynamoDbKeyRange {
    val (startOffset, count) = decodeSegment(keyRange.start)
    val (endOffset, _) = decodeSegment(keyRange.end)
    return DynamoDbKeyRange(startOffset, endOffset, count)
  }

  private fun encodeKeyRange(
    start: Int,
    end: Int,
    count: Int
  ): KeyRange {
    return KeyRange.Builder()
        .start(encodeSegment(start, count))
        .end(encodeSegment(end, count))
        .build()
  }

  override fun runBatch(request: RunBatchRequest): RunBatchResponse {
    val (start, end, count) = decodeKeyRange(request.batch_range)
    require(end == start + 1)

    println("running segment $start of $count")

    var lastEvaluatedKey: Map<String, AttributeValue>? = null
    do {
      val scanRequest = DynamoDBScanExpression().apply {
        segment = start
        totalSegments = count
        limit = 4
        if (lastEvaluatedKey != null) {
          exclusiveStartKey = lastEvaluatedKey
        }
      }
      val result = dynamoDb.scanPage(backfill.itemType, scanRequest)
      val updates = backfill.runBatch(result.results, config)
      if (updates.isNotEmpty()) {
        dynamoDb.batchSave(updates)
      }
      lastEvaluatedKey = result.lastEvaluatedKey
    } while (lastEvaluatedKey != null)

    return RunBatchResponse.Builder()
        .build()
  }
}

interface DynamoBackfill<I : Any, P : Any> {

  val itemType: Class<I>

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: BackfillConfig<I>) {}

  /**
   * Called for each batch of matching records.
   * Override in a backfill to process all records in a batch.
   */
  open fun runBatch(items: List<I>, config: BackfillConfig<P>): List<I> {
    val updates = mutableListOf<I>()
    for (it in items) {
      val update = runOne(it, config) ?: continue
      updates += update
    }
    return updates
  }

  /**
   * Called for each matching record.
   * Override in a backfill to process one record at a time.
   *
   * Returns non-null to replace the stored item.
   */
  open fun runOne(item: I, config: BackfillConfig<P>): I? {
    return null
  }
}
