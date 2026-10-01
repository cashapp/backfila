package app.cash.backfila.client.jooq

import org.jooq.DSLContext

/**
 * Intercepts the queries a [JooqBackfill] generates to iterate its records.
 *
 * The returned [DSLContext] is used for the record-iteration queries only — the boundary key lookups, the batch range
 * scans and the key selection that precedes each batch. Work a backfill does itself in [JooqBackfill.backfill] runs on
 * the transacter it is handed and is not affected.
 *
 * This is the jOOQ counterpart of `SqlDelightQueryInterceptor`, and differs from it deliberately. SQLDelight does not
 * expose its generated SQL, so there the interceptor wraps query *execution* and the caller scopes a driver decorator to
 * that window. jOOQ builds its queries against a [DSLContext], so here the interceptor is handed that context and
 * returns the one to use. Deriving a context is already scoped, so there is no thread-local window to manage and no
 * requirement that execution stay on the calling thread.
 */
fun interface JooqQueryInterceptor {
  /**
   * Returns the [DSLContext] the generated queries should run on, given the [dslContext] the backfill's transacter
   * supplied. Implementations that derive a context should derive from this one rather than build a fresh one, so the
   * transaction the queries run in is preserved.
   */
  fun intercept(dslContext: DSLContext): DSLContext

  companion object {
    /** Leaves the transacter's context alone. The default for a [JooqBackfill]. */
    @JvmField
    val NONE: JooqQueryInterceptor = JooqQueryInterceptor { it }
  }
}
