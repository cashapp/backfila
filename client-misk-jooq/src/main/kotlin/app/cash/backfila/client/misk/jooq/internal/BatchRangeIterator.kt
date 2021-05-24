package app.cash.backfila.client.misk.jooq.internal

import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.jooq.JooqBackfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import com.google.common.base.Stopwatch
import com.google.common.collect.AbstractIterator
import org.jooq.DSLContext
import java.lang.IllegalStateException
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Helper class that helps us iterate over batch ranges.
 */
class BatchRangeIterator<K, Param : Any>(
  private val jooqBackfill: JooqBackfill<K, Param>,
  private val session: DSLContext,
  private val request: GetNextBatchRangeRequest,
  private val config: BackfillConfig<Param>
) : AbstractIterator<GetNextBatchRangeResponse.Batch>() {

  private val timeElapsed: Supplier<Boolean>
  private var nextKeyRange: OpenKeyRange<K>
  init {
    timeElapsed = if (request.compute_time_limit_ms == null) Supplier { false } else timer(
      request.compute_time_limit_ms
    )
    nextKeyRange = OpenKeyRange.initialRangeFor(jooqBackfill, request, session)
  }

  private fun timer(timeLimitMs: Long): Supplier<Boolean> {
    val stopwatch = Stopwatch.createStarted()
    return Supplier { stopwatch.elapsed(TimeUnit.MILLISECONDS) > timeLimitMs }
  }

  override fun computeNext(): GetNextBatchRangeResponse.Batch? {
    if (timeElapsed.get() || request.backfill_range.start == null) return endOfData()
    val keyRange = nextKeyRange
    val keyValues = selectKeyValues(keyRange)
    val start = keyRange.determineStart(keyValues)
    val end = keyRange.determineEnd(keyValues)
    val scannedCount = determineScannedCount(keyRange, end)
    if (scannedCount == 0) return endOfData()
    nextKeyRange = keyRange.nextRangeFor(end)
    return GetNextBatchRangeResponse.Batch.Builder()
      .batch_range(jooqBackfill.buildKeyRange(start, end))
      .scanned_record_count(scannedCount.toLong())
      .matching_record_count(Integer.toUnsignedLong(keyValues.size))
      .build()
  }

  private fun selectKeyValues(keyRange: OpenKeyRange<K>): List<K> {
    val limit = if (request.precomputing == true) request.scan_size else request.batch_size
    return session.select(jooqBackfill.compoundKeyFields)
      .from(jooqBackfill.table)
      .where(jooqBackfill.filterCondition(config))
      .and(keyRange.betweenStartAndUpperBoundCondition())
      .orderBy(jooqBackfill.compoundKeyFields)
      .limit(limit)
      .fetch { jooqBackfill.recordToKey(it) }
  }

  /**
   * This represents the total rows not including the filter condition.
   * In a scan range, we might have a 1000 rows, but only 100 might match the filter
   * condition that the backfill will operate on.
   */
  private fun determineScannedCount(keyRange: OpenKeyRange<K>, end: K): Int {
    return session.selectCount()
      .from(jooqBackfill.table)
      .where(keyRange.betweenStartAndEndCondition(end))
      .fetchOne()
      ?.value1()
      ?: throw IllegalStateException("A SQL count will always return back a row")
  }
}
