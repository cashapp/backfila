package app.cash.backfila.client.jooq.internal

import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.client.jooq.CompoundKeyComparer
import app.cash.backfila.client.jooq.CompoundKeyComparisonOperator
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.select

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
   * The overall upper bound of the range.
   */
  private val upperBound: K
) {

  fun determineStart(keyValues: List<K>): K =
    keyValues.firstOrNull() ?: start

  fun determineEnd(keyValues: List<K>): K =
    keyValues.lastOrNull() ?: upperBound

  /**
   * Builds a jooq condition that limits a query to the start of the range and the overall upper
   * bound.
   */
  fun betweenStartAndUpperBoundCondition(): Condition {
    return jooqBackfill.compareCompoundKey(start, startComparison)
      .and(
        jooqBackfill.compareCompoundKey(upperBound) { keyCompare, compoundKeyValue ->
          keyCompare.lte(compoundKeyValue)
        }
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
        }
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
      }
    )
  }

  companion object {
    /**
     * Builds the initial range that we start with when iterating over a sequence of ranges.
     */
    fun <K> initialRangeFor(
      jooqBackfill: JooqBackfill<K, *>,
      request: GetNextBatchRangeRequest,
      session: DSLContext
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
              compoundKeyValue
            )
          }
        }

      val start = request.previous_end_key
        ?.let {
          jooqBackfill.fromByteString(request.previous_end_key)
        } ?: jooqBackfill.fromByteString(request.backfill_range.start)

      return OpenKeyRange(
        jooqBackfill = jooqBackfill,
        start = start,
        startComparison = startComparison,
        upperBound = computeUpperBound(
          jooqBackfill, request, session,
          jooqBackfill.compareCompoundKey(start, startComparison)
        )
      )
    }

    /**
     * Get the upper bound of the scan range without applying any backfill filter conditions.
     * This is so that we can restrict the upper bound when the filter is applied subsequently.
     * If we don't restrict the range, then the query could perform badly.
     *
     * This SQL is essentially going to look like
     * <pre>
     * select <backfill fields>
     *   from (
     *      select <backfill fields>
     *      from <backfill table>
     *      where (backfill fields > end of previous range or >= start of backfill)
     *      order by <backfill fields> asc
     *      limit <scan size>
     *      )
     *  order by <backfill fields> desc
     *  limit 1
     * </pre>
     */
    private fun <K> computeUpperBound(
      jooqBackfill: JooqBackfill<K, *>,
      request: GetNextBatchRangeRequest,
      session: DSLContext,
      afterPreceedingRowsCondition: Condition
    ): K {
      return if (request.backfill_range != null && request.backfill_range.end != null) {
        jooqBackfill.fromByteString(request.backfill_range.end)
      } else {
        session
          .select(jooqBackfill.compoundKeyFields)
          .from(
            select(jooqBackfill.compoundKeyFields)
              .from(jooqBackfill.table)
              .where(afterPreceedingRowsCondition)
              .orderBy(jooqBackfill.sortingByCompoundKeyFields { it.asc() })
              .limit(request.scan_size)
          )
          .orderBy(jooqBackfill.sortingByCompoundKeyFields { it.desc() })
          .limit(1)
          .fetchOne { jooqBackfill.recordToKey(it) }
          ?: throw IllegalStateException(
            "Expecting a row when calculating the upper bound"
          )
      }
    }
  }
}
