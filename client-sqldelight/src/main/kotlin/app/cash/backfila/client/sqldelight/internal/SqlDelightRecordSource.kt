package app.cash.backfila.client.sqldelight.internal

import app.cash.backfila.client.sqldelight.BoundingRangeStrategy
import app.cash.backfila.client.sqldelight.DefaultBoundingRangeStrategy
import app.cash.backfila.client.sqldelight.KeyEncoder
import app.cash.backfila.client.sqldelight.SqlDelightRecordSourceConfig
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse.Batch
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.RunBatchRequest
import com.google.common.base.Stopwatch

class SqlDelightRecordSource<K : Any, R : Any>(
  private val recordSourceQueries: SqlDelightRecordSourceConfig<K, R>,
  private val boundingRangeStrategy: BoundingRangeStrategy<K> = DefaultBoundingRangeStrategy(),
) {
  val keyEncoder = recordSourceQueries.keyEncoder

  fun validateRange(range: KeyRange) {
    range.validate(keyEncoder)
  }

  fun computeOverallRange(partitionName: String, requestedRange: KeyRange): KeyRange {
    if (requestedRange.start != null && requestedRange.end != null) {
      return requestedRange
    }

    val minMax = boundingRangeStrategy.computeAbsoluteRange(partitionName, recordSourceQueries)
    return if (minMax.min == null && minMax.max == null) {
      // Both are null so it is an empty table, no work to do for this partition.
      KeyRange.Builder().build()
    } else {
      require(minMax.min != null && minMax.max != null) {
        "computeAbsoluteRange failed to return a min or a max. minMax $minMax"
      }
      KeyRange.Builder()
        .start(requestedRange.start ?: keyEncoder.encode(minMax.min))
        .end(requestedRange.end ?: keyEncoder.encode(minMax.max))
        .build()
    }
  }

  fun getBatchGenerator(request: GetNextBatchRangeRequest): BatchGenerator {
    return BatchGenerator(request)
  }

  inner class BatchGenerator(request: GetNextBatchRangeRequest) {
    private val partitionName: String = request.partition_name // TODO How to do sharding.
    private val batchSize = request.batch_size
    private val scanSize = request.scan_size
    private val rangeStart = keyEncoder.decode(request.backfill_range.start)
    private val rangeEnd = keyEncoder.decode(request.backfill_range.end)
    private val precomputing: Boolean = request.precomputing == true

    // Initialized from the request and gets updated as batches are returned.
    private var previousEndKey: K? = request.previous_end_key?.let { keyEncoder.decode(it) }
    private var boundingMax: K? = null

    operator fun next(): Batch? {
      // Scan a big chunk of records to have a reasonable bound for the next query.
      // We find all matching batches in each scan bound to avoid repeating this work.
      var batchBoundingMax = boundingMax
      if (batchBoundingMax == null) {
        val stopwatch = Stopwatch.createStarted()
        // Use the bounding range strategy to compute the max bound.
        // For Vitess, this may query shards in parallel to avoid the 100k row limit.
        batchBoundingMax = boundingRangeStrategy.computeBoundingRangeMax(
          partitionName = partitionName,
          previousEndKey = previousEndKey,
          rangeStart = rangeStart,
          rangeEnd = rangeEnd,
          scanSize = scanSize,
          queries = recordSourceQueries,
        )

        if (batchBoundingMax == null) {
          // Bounding range returned no records, done computing batches.
          return null
        }
      }

      val txResult = produceBatch(batchBoundingMax) // TODO create a transaction and deal with sharding.

      this.boundingMax = when {
        // Reached the end of this bounding range, null it out so a new one is computed when more
        // batches are requested.
        txResult.end == batchBoundingMax -> null
        else -> batchBoundingMax
      }

      previousEndKey = keyEncoder.decode(txResult.batch.batch_range.end)
      return txResult.batch
    }

    private fun produceBatch(batchBoundingMax: K): TxResult {
      val batchEndPkey: K? = if (precomputing) {
        // No need to find correct-sized batches, just quickly compute a count.
        null
      } else {
        // Now that we have a bound, this query can find criteria-matching batches without
        // becoming a long-running query.
        if (previousEndKey == null) {
          recordSourceQueries.produceInitialBatchFromRange(rangeStart, batchBoundingMax, batchSize - 1).executeAsOneOrNull()
        } else {
          recordSourceQueries.produceNextBatchFromRange(previousEndKey!!, batchBoundingMax, batchSize - 1).executeAsOneOrNull()
        }
      }

      val matchingCount: Long
      val end: K
      if (batchEndPkey == null) {
        // Less than batchSize matches, so return the end of the scan size and count the matches.
        matchingCount = if (previousEndKey == null) {
          recordSourceQueries.countInitialBatchMatches(rangeStart, batchBoundingMax).executeAsOne().toLong()
        } else {
          recordSourceQueries.countNextBatchMatches(previousEndKey!!, batchBoundingMax).executeAsOne().toLong()
        }
        end = batchBoundingMax
      } else {
        // We got an id, so there's exactly batchSize results.
        matchingCount = batchSize
        end = batchEndPkey
      }

      // Get start pkey and scanned record count for this batch.
      val result = if (previousEndKey == null) {
        recordSourceQueries.getInitialStartKeyAndScanCount(rangeStart, end).executeAsOne()
      } else {
        recordSourceQueries.getNextStartKeyAndScanCount(previousEndKey!!, end).executeAsOne()
      }
      require(result.min != null) {
        "getInitialStartKeyAndScanCount or getNextStartKeyAndScanCount query failed to return a min and/or count. result: $result"
      }
      val start = keyEncoder.encode(result.min)
      val scannedCount = result.count

      val batch = Batch.Builder()
        .batch_range(
          KeyRange.Builder()
            .start(start)
            .end(keyEncoder.encode(end))
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
    val start = keyEncoder.decode(request.batch_range.start)
    val end = keyEncoder.decode(request.batch_range.end)
    return recordSourceQueries.getBatch(start, end).executeAsList()
  }
}

/** Confirms that [keyEncoder] can decode this range. */
fun KeyRange.validate(keyEncoder: KeyEncoder<*>) {
  try {
    start?.let { keyEncoder.decode(it) }
  } catch (e: Exception) {
    throw IllegalArgumentException("failed to decode start key", e)
  }
  try {
    end?.let { keyEncoder.decode(it) }
  } catch (e: Exception) {
    throw IllegalArgumentException("failed to decode end key", e)
  }
}
