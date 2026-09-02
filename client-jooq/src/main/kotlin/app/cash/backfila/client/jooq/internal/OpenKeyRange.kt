package app.cash.backfila.client.jooq.internal

import app.cash.backfila.client.jooq.CompoundKeyComparer
import app.cash.backfila.client.jooq.CompoundKeyComparisonOperator
import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record

data class OpenKeyRange<K>(
  private val jooqBackfill: JooqBackfill<K, *>,
  /**
   * Comparison function to use as a bound for the start value. Generally set to either
   * `CompoundKeyComparer::gte` or `CompoundKeyComparer::gt` depending on whether the start of the
   * range is inclusive or exclusive.
   */
  private val startComparison: CompoundKeyComparisonOperator<K>,
  /**
   * The start of the range.
   */
  private val start: K,
  /**
   * The upper bound of the current raw scan window.
   */
  private val upperBound: K,
) {

  fun determineStart(keyValues: List<K>): K =
    keyValues.firstOrNull() ?: start

  fun determineEnd(keyValues: List<K>): K =
    keyValues.lastOrNull() ?: upperBound

  internal fun determineStart(session: DSLContext): K {
    return session.select(jooqBackfill.compoundKeyFields)
      .from(jooqBackfill.table)
      .where(betweenStartAndUpperBoundCondition())
      .orderBy(jooqBackfill.sortingByCompoundKeyFields { it.asc() })
      .limit(1)
      .fetchOne { jooqBackfill.recordToKey(it) }
      ?: start
  }

  internal fun determineEnd(
    keyValues: List<K>,
    request: GetNextBatchRangeRequest,
  ): K {
    return if (request.precomputing == true || keyValues.size.toLong() < request.batch_size) {
      upperBound
    } else {
      keyValues.last()
    }
  }

  internal fun endsAtUpperBound(end: K): Boolean =
    jooqBackfill.toByteString(end) == jooqBackfill.toByteString(upperBound)

  /**
   * Builds a jooq condition that limits a query to the start of the range and the current raw scan
   * window's upper bound.
   */
  fun betweenStartAndUpperBoundCondition(): Condition {
    return jooqBackfill.compareCompoundKey(start, startComparison)
      .and(
        jooqBackfill.compareCompoundKey(upperBound) { keyCompare, compoundKeyValue ->
          keyCompare.lte(compoundKeyValue)
        },
      )
  }

  /**
   * Builds a jooq condition that limits a query to the start of the range and the provided
   * `end`.
   */
  fun betweenStartAndEndCondition(end: K): Condition {
    return jooqBackfill.compareCompoundKey(start, startComparison)
      .and(
        jooqBackfill.compareCompoundKey(end) { keyCompare, compoundKeyValue ->
          keyCompare.lte(compoundKeyValue)
        },
      )
  }

  /**
   * Builds a range that comes after the provided `end`.
   */
  fun nextRangeFor(end: K): OpenKeyRange<K> {
    return OpenKeyRange(
      jooqBackfill = jooqBackfill,
      upperBound = upperBound,
      start = end,
      startComparison = { keyCompare: CompoundKeyComparer<K>, compoundKeyValue: Record ->
        keyCompare.gt(compoundKeyValue)
      },
    )
  }

  companion object {
    /**
     * Builds the initial range that we start with when iterating over a sequence of ranges.
     */
    fun <K> initialRangeFor(
      jooqBackfill: JooqBackfill<K, *>,
      request: GetNextBatchRangeRequest,
      session: DSLContext,
    ): OpenKeyRange<K> {
      // If this is the first batch, we want to start with the provided value on the backfila
      // screen. If not, then, we need to start with one after the previous end value
      val startComparison: CompoundKeyComparisonOperator<K> =
        if (request.previous_end_key == null) {
          { keyComparer: CompoundKeyComparer<K>, compoundKeyValue: Record ->
            keyComparer.gte(compoundKeyValue)
          }
        } else {
          { keyComparer: CompoundKeyComparer<K>, compoundKeyValue: Record ->
            keyComparer.gt(
              compoundKeyValue,
            )
          }
        }

      val start = request.previous_end_key
        ?.let {
          jooqBackfill.fromByteString(request.previous_end_key)
        } ?: jooqBackfill.fromByteString(request.backfill_range.start)

      return rangeFor(
        jooqBackfill = jooqBackfill,
        request = request,
        session = session,
        start = start,
        startComparison = startComparison,
      )
    }

    internal fun <K> nextRangeFor(
      jooqBackfill: JooqBackfill<K, *>,
      request: GetNextBatchRangeRequest,
      session: DSLContext,
      start: K,
    ): OpenKeyRange<K> {
      return rangeFor(
        jooqBackfill = jooqBackfill,
        request = request,
        session = session,
        start = start,
        startComparison = { keyComparer, compoundKeyValue ->
          keyComparer.gt(compoundKeyValue)
        },
      )
    }

    private fun <K> rangeFor(
      jooqBackfill: JooqBackfill<K, *>,
      request: GetNextBatchRangeRequest,
      session: DSLContext,
      start: K,
      startComparison: CompoundKeyComparisonOperator<K>,
    ): OpenKeyRange<K> {
      val upperBound = computeUpperBound(
        jooqBackfill = jooqBackfill,
        request = request,
        session = session,
        afterPrecedingRowsCondition = jooqBackfill.compareCompoundKey(start, startComparison),
      )
      return if (upperBound != null) {
        OpenKeyRange(jooqBackfill, startComparison, start, upperBound)
      } else {
        OpenKeyRange(
          jooqBackfill = jooqBackfill,
          startComparison = { keyComparer, compoundKeyValue ->
            keyComparer.gt(compoundKeyValue)
          },
          start = start,
          upperBound = start,
        )
      }
    }

    /**
     * Get the upper bound of the scan range without applying any backfill filter conditions.
     * This is so that we can restrict the upper bound when the filter is applied subsequently.
     * If we don't restrict the range, then the query could perform badly.
     *
     * The bound is the `scan_size`th row of the window:
     * <pre>
     * select <backfill fields>
     *   from <backfill table>
     *  where (backfill fields > end of previous range or >= start of backfill)
     *    and backfill fields <= end of the overall backfill range
     *  order by <backfill fields> asc
     *  limit 1 offset <scan size> - 1
     * </pre>
     *
     * A backfill's final window holds fewer than `scan_size` rows, so that returns nothing and we
     * fall back to the largest key in the window.
     *
     * Both queries read the backfill's own key fields from its own table. Selecting them from a
     * derived table instead would require resolving them by column name, which fails for key
     * fields that are expressions rather than plain columns.
     */
    private fun <K> computeUpperBound(
      jooqBackfill: JooqBackfill<K, *>,
      request: GetNextBatchRangeRequest,
      session: DSLContext,
      afterPrecedingRowsCondition: Condition,
    ): K? {
      val overallEnd = request.backfill_range?.end?.let(jooqBackfill::fromByteString)
      val boundedCondition = overallEnd?.let {
        afterPrecedingRowsCondition.and(
          jooqBackfill.compareCompoundKey(it) { keyCompare, compoundKeyValue ->
            keyCompare.lte(compoundKeyValue)
          },
        )
      } ?: afterPrecedingRowsCondition

      fun selectBoundedWindow() = session
        .select(jooqBackfill.compoundKeyFields)
        .from(jooqBackfill.table)
        .where(boundedCondition)

      val lastKeyOfFullWindow = selectBoundedWindow()
        .orderBy(jooqBackfill.sortingByCompoundKeyFields { it.asc() })
        .limit(1)
        .offset(maxOf(request.scan_size - 1, 0))
        .fetchOne { jooqBackfill.recordToKey(it) }
      if (lastKeyOfFullWindow != null) return lastKeyOfFullWindow

      return selectBoundedWindow()
        .orderBy(jooqBackfill.sortingByCompoundKeyFields { it.desc() })
        .limit(1)
        .fetchOne { jooqBackfill.recordToKey(it) }
    }
  }
}
