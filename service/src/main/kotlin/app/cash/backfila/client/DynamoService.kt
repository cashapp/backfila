package app.cash.backfila.client

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * This is a naive implementation of Backfila target. Its problem is there's one batch per DynamoDB
 * partition, and that batch is run in a single step.
 */
class DynamoService {
  val PARTITION_COUNT_PARAMETER = "partition_count"

  fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
    val partitionCount = partitionCount(request.parameters)

    val partitions = mutableListOf<PrepareBackfillResponse.Partition>()
    for (i in 0 until partitionCount) {
      val partition = PrepareBackfillResponse.Partition.Builder()
          .partition_name(i.toString())
          .backfill_range(keyRange())
          .estimated_record_count(100L)
          .build()
      partitions += partition
    }

    return PrepareBackfillResponse.Builder()
        .partitions(partitions)
        .build()
  }

  private fun partitionCount(parametersMap: Map<String, ByteString>): Int {
    val byteString = parametersMap[PARTITION_COUNT_PARAMETER] ?: error("partition_count is required")
    return byteString.utf8().toInt()
  }

  private fun keyRange(): KeyRange? {
    return KeyRange.Builder()
        .start("0".encodeUtf8())
        .end("1".encodeUtf8())
        .build()
  }

  fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
    if (request.previous_end_key == "0".encodeUtf8()) {
      // return a batch
      return GetNextBatchRangeResponse.Builder()
          .batches(listOf(GetNextBatchRangeResponse.Batch.Builder()
              .batch_range(keyRange())
              .build()))
          .build()
    } else if (request.previous_end_key == "1".encodeUtf8()) {
      // return no more batches
      return GetNextBatchRangeResponse.Builder()
          .batches(listOf())
          .build()
    } else {
      error("unexpected previous_end_key ${request.previous_end_key}")
    }
  }

  suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
    // TODO: query dynamo for the named partition
    //       do all the backfilling

    val segment = request.partition_name.toInt()
    val totalSegments = partitionCount(request.parameters)
    backfillDynamoDb(segment, totalSegments)

    return RunBatchResponse.Builder()
        .build()
  }

  private fun backfillDynamoDb(segment: Int, totalSegments: Int) {


  }
}

/*
NEXT STEPS

1. Write a real unit test with the gross limitation that there's no partial results
   within a partition

    This test exercises DynamoService, but needs a FakeBackfill implementation

2. Implement the optimization of 2-scans, one for batching and another for executing
   Ie. make getNextBatchRange do real work rather than returning one hardcoded 0..1 batch

deprecate this, make it depend on client-misk-hibernate
  client-misk

  client-misk-hibernate
  client-misk-core
  client-misk-dynamodb

Dynamo Query over table from 0...10000 (sparse)
partition this 10 ways
partition 3 is 3000..4000

scan parition 3 of 10, asking for 25 rows
  MY RANGES will be what Backfila passed in as the start, and LastEvaluatedKey as the end
  rows 3003 through 3081

  THIS DEPENDS ON COUPLING TWO FEATURES
    - partitioning in DynamoDB API
    - PLUS constraining the query by key

    'WHERE KEY IS > ?'

getNextBatchRange
 - do a real scan without looking at the data
 - get the partition_key/range_key thingies of the range



PROBLEM: how do we not create gaps as segments and keys are rebalanced etc.

hypothesis:
 - in the phase where we break a partition into batch ranges, we translate the inbound SEGMENTS
 into DYNAMODB KEY RANGES
 - in the phase where we runBatches, we ONLY USE KEY RANGES
 - THE CONSTRAINT ON THIS HYPOTHESIS IS key ranges are bounded by partitions

 MySQL - hash(customer ID) to shard
 DynamoDB - ranges to partitions


 Map the big table to a set of segments
 Map the segment to a range of rows
 -- hypothesis to prove -- does a range of rows target a single segment

 0..10000

 0...2500 SEGMENT 1 Query Segment 1 (x mod(4) = 1)
 2500..5050 SEGMENT 2
 5050..7500 SEGMENT 3
 7500..10000 SEGMENT 3


QUESTION:
 - is it useful to specify a segment + totalSegments in the runBatch phase?
    - yes if segmenting is stable
    - no if segmenting is not stable

LastEvaluatedKey IS STABLE
  if I scanned 0 through LastEvaluatedKey=K5
  then the data is organized so that I can say (partition=30 of 64, offset is K5)

  WITHIN A SEGMENT DATA IS SORTED


 1, 2, 3 ,4 ,5, 10, 18, 21, 22, 23, 25

getNextBatchRange
 (null through 18]
      SCAN
         WHERE PARTIION IS 3 OF 100
         WHERE LastEvaluatedKey IS NULL
         AND key <= 10

 (10 through 20]
 (20 through null]

 1, 2, 13, 15, 20, 25

runBatch("(0 through 18]") 1,2,3


1,............4,5,6,7,8,9,10,11,12,13,14,18

getNextBatchRange phase -- odd numbers, page size 3, scan limit 4
  ..4]
  (4..10]
  (10..14]
  (14..

runBatch("..4]")

scanning is SEQUENTIAL


 */

