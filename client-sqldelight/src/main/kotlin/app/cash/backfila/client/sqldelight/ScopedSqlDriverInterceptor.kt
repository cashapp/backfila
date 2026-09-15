package app.cash.backfila.client.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * A [SqlDriver] decorator and [SqlDelightQueryInterceptor] that transforms SQL only within [intercept].
 *
 * Build the SQLDelight database with this driver, then pass the same instance to an individual Backfila. This is for
 * synchronous drivers; query execution must remain on the thread that called [intercept].
 */
class ScopedSqlDriverInterceptor(
  private val delegate: SqlDriver,
  private val transformSql: (String) -> String,
) : SqlDriver by delegate, SqlDelightQueryInterceptor {
  private val interceptionDepth = ThreadLocal.withInitial { 0 }

  override fun <T> intercept(block: () -> T): T {
    interceptionDepth.set(interceptionDepth.get() + 1)
    try {
      return block()
    } finally {
      val remainingDepth = interceptionDepth.get() - 1
      if (remainingDepth == 0) interceptionDepth.remove() else interceptionDepth.set(remainingDepth)
    }
  }

  override fun <R> executeQuery(
    identifier: Int?,
    sql: String,
    mapper: (SqlCursor) -> QueryResult<R>,
    parameters: Int,
    binders: (SqlPreparedStatement.() -> Unit)?,
  ): QueryResult<R> =
    delegate.executeQuery(
      identifier,
      if (interceptionDepth.get() > 0) transformSql(sql) else sql,
      mapper,
      parameters,
      binders,
    )
}
