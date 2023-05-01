package app.cash.backfila.client.sqldelight

import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import com.google.common.base.Stopwatch

class SqlDelightRowSource<K : Any, R : Any>(
  val transacter: Transacter, // The query has the transacter part of it? How is sharding meant to work? Might make more sense if each function took in the transacter to operate on.
  val keyConverter: KeyConverter<K>,
  private val selectOverallRange: Query<MinMax<K>>,
  private val selectInitialMaxBound: (rangeStart: K, rangeEnd: K, scanSize: Long) -> Query<NullKeyContainer<K>>,
  private val selectNextMaxBound: (previousEndKey: K, rangeEnd: K, scanSize: Long) -> Query<NullKeyContainer<K>>,
  private val produceInitialBatchFromRange: (rangeStart: K, boundingMax: K, offset: Long) -> Query<K>,
  private val produceNextBatchFromRange: (previousEndKey: K, boundingMax: K, offset: Long) -> Query<K>,
  private val countInitialBatchMatches: (rangeStart: K, boundingMax: K) -> Query<Long>,
  private val countNextBatchMatches: (previousEndKey: K, boundingMax: K) -> Query<Long>,
  private val getInitialStartKeyAndScanCount: (rangeStart: K, batchEnd: K) -> Query<MinAndCount<K>>,
  private val getNextStartKeyAndScanCount: (previousEndKey: K, batchEnd: K) -> Query<MinAndCount<K>>,
  private val getBatch: (start: K, end: K) -> Query<R>,
) {

  /**
   * TODO maybe this should go somewhere else.
   */
  fun validateRange(range: KeyRange) {
    if (range == null) return
    range.start?.let {
      keyConverter.toKeyOrNull(it) ?: error("Start of range must be a valid key.")
    }
    range.end?.let {
      keyConverter.toKeyOrNull(it) ?: error("End of range must be a valid key.")
    }
  }

  data class MinMax<K>(
    val min: K?,
    val max: K?,
  )

  fun computeOverallRange(requestedRange: KeyRange): KeyRange {
    if (requestedRange.start != null && requestedRange.end != null) {
      return requestedRange
    }

    val minMax = selectOverallRange.executeAsOneOrNull()
    return if (minMax == null) {
      // Empty table, no work to do for this partition.
      KeyRange.Builder().build()
    } else {
      require(minMax.min != null && minMax.max != null) {
        "selectOverallRange query failed to return a min and/or max. minMax $minMax"
      }
      KeyRange.Builder()
        .start(requestedRange?.start ?: keyConverter.toBytes(minMax.min))
        .end(requestedRange?.end ?: keyConverter.toBytes(minMax.max))
        .build()
    }
  }

  fun getBatchGenerator(request: GetNextBatchRangeRequest): BatchGenerator {
    return BatchGenerator(request)
  }

  data class NullKeyContainer<K>(
    val key: K?,
  )

  data class MinAndCount<K>(
    val min: K?,
    val count: Long,
  )

  inner class BatchGenerator(request: GetNextBatchRangeRequest) {
    private val partitionName: String = request.partition_name // TODO How to do sharding.
    private val batchSize = request.batch_size
    private val scanSize = request.scan_size
    private val rangeStart = keyConverter.toKey(request.backfill_range.start)
    private val rangeEnd = keyConverter.toKey(request.backfill_range.end)
    private val precomputing: Boolean = request.precomputing == true

    // Initialized from the request and gets updated as batches are returned.
    private var previousEndKey: K? = keyConverter.toKeyOrNull(request.previous_end_key)
    private var boundingMax: K? = null

    operator fun next(): Batch? {
      // Scan a big chunk of records to have a reasonable bound for the next query.
      // We find all matching batches in each scan bound to avoid repeating this work.
      if (boundingMax == null) {
        val stopwatch = Stopwatch.createStarted()
        boundingMax = if (previousEndKey == null) {
          selectInitialMaxBound(rangeStart, rangeEnd, scanSize).executeAsOne().key
        } else {
          selectNextMaxBound(previousEndKey!!, rangeEnd, scanSize).executeAsOne().key
        }

        if (boundingMax == null) {
          // Bounding range returned no records, done computing batches. TODO: Log this?
          return null
        }
        /*  TODO: Log this?
        logger.info(
          "Computed scan bound for next batch: [$previousEndKey, $boundingMax]. " +
            "Took $stopwatch"
        )*/
      }

      val txResult = produceBatch() // TODO create a transaction and deal with sharding.

      if (txResult.end == boundingMax) {
        // Reached the end of this bounding range, null it out so a new one is computed when more
        // batches are requested.
        boundingMax = null
      }
      previousEndKey = keyConverter.toKey(txResult.batch.batch_range.end)
      return txResult.batch
    }

    private fun produceBatch(): TxResult {
      val batchEndPkey: K? = if (precomputing) {
        // No need to find correct-sized batches, just quickly compute a count.
        null
      } else {
        // Now that we have a bound, this query can find criteria-matching batches without
        // becoming a long running query.
        // Hibernate doesn't support subqueries in FROM, but we can use a limit+offset
        // to figure out the last id in the batch. Where offset = batchSize - 1.
        // We can't use raw SQL as above because we're working with a backfill-provided Criteria.
        if (previousEndKey == null) {
          produceInitialBatchFromRange(rangeStart, boundingMax!!, batchSize - 1).executeAsOneOrNull()
        } else {
          produceNextBatchFromRange(previousEndKey!!, boundingMax!!, batchSize - 1).executeAsOneOrNull()
        }
      }

      val matchingCount: Long
      val end: K
      if (batchEndPkey == null) {
        // Less than batchSize matches, so return the end of the scan size and count the matches.
        matchingCount = if (previousEndKey == null) {
          countInitialBatchMatches(rangeStart, boundingMax!!).executeAsOne().toLong()
        } else {
          countNextBatchMatches(previousEndKey!!, boundingMax!!).executeAsOne().toLong()
        }
        end = boundingMax!!
      } else {
        // We got an id, so there's exactly batchSize results.
        matchingCount = batchSize
        end = batchEndPkey
      }

      // Get start pkey and scanned record count for this batch.
      val result = if (previousEndKey == null) {
        getInitialStartKeyAndScanCount(rangeStart, end!!).executeAsOne()
      } else {
        getNextStartKeyAndScanCount(previousEndKey!!, end!!).executeAsOne()
      }
      require(result.min != null && result.count != null) {
        "getInitialStartKeyAndScanCount or getNextStartKeyAndScanCount query failed to return a min and/or count. result: $result"
      }
      val start = keyConverter.toBytes(result.min)
      val scannedCount = result.count

      val batch = Batch.Builder()
        .batch_range(
          KeyRange.Builder()
            .start(start)
            .end(keyConverter.toBytes(end))
            .build(),
        )
        .scanned_record_count(scannedCount.toLong())
        .matching_record_count(matchingCount.toLong() ?: 0L)
        .build()
      return TxResult(end, batch)
    }
    private inner class TxResult(val end: K, val batch: Batch)
  }

  fun getBatchData(request: RunBatchRequest): List<R> {
    // TODO create a transaction and deal with sharding.
    val start = keyConverter.toKey(request.batch_range.start)
    val end = keyConverter.toKey(request.batch_range.end)
    return getBatch(start, end).executeAsList()
  }
}
