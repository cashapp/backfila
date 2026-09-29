package app.cash.backfila.client.jooq

import org.jooq.DSLContext
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListenerProvider

/**
 * A [JooqQueryInterceptor] that rewrites the SQL of the queries a [JooqBackfill] generates.
 *
 * For the cases a [org.jooq.Condition] cannot reach. The generated queries are built by the library, so a backfill
 * cannot annotate them, and the boundary lookups have no `WHERE` clause to restrict in the first place. A database that
 * needs a directive on those statements — a routing hint, an optimizer hint — can supply it here.
 *
 * [transformSql] receives each generated statement after rendering and returns the SQL to execute. It sees only the
 * statements this interceptor is installed for, but it sees all of them, so it should return anything it does not mean
 * to change untouched rather than assume a shape.
 *
 * Nothing here is specific to one database. A Vitess caller wanting to permit a deliberate scatter, for example, writes
 * the directive itself:
 *
 * ```kotlin
 * override val queryInterceptor = SqlTransformingQueryInterceptor { sql ->
 *   if (sql.trimStart().startsWith("select", ignoreCase = true)) {
 *     sql.replaceFirst("select", "select /*vt+ ALLOW_SCATTER */", ignoreCase = true)
 *   } else {
 *     sql
 *   }
 * }
 * ```
 */
class SqlTransformingQueryInterceptor(
  private val transformSql: (String) -> String,
) : JooqQueryInterceptor {
  override fun intercept(dslContext: DSLContext): DSLContext =
    DSL.using(
      dslContext.configuration()
        .deriveAppending(DefaultExecuteListenerProvider(RenderedSqlTransformer(transformSql))),
    )

  /**
   * Rewrites at `renderEnd`, the last point before the statement is prepared, so [transformSql] sees the SQL jOOQ
   * actually produced rather than a guess at it.
   */
  private class RenderedSqlTransformer(
    private val transformSql: (String) -> String,
  ) : ExecuteListener {
    override fun renderEnd(ctx: ExecuteContext) {
      val sql = ctx.sql() ?: return
      ctx.sql(transformSql(sql))
    }
  }
}
