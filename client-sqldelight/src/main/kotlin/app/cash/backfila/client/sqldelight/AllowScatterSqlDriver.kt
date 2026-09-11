package app.cash.backfila.client.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

/**
 * A [SqlDriver] decorator that adds Vitess's `ALLOW_SCATTER` hint while opted-in Backfila queries execute.
 *
 * Build the SQLDelight database with this driver, then set `allowScatter = true` on the Backfila record source config.
 * Queries outside Backfila are unchanged.
 */
class AllowScatterSqlDriver(private val delegate: SqlDriver) : SqlDriver by delegate {
  private val allowScatterDepth = ThreadLocal.withInitial { 0 }

  internal fun <T> allowingScatter(block: () -> T): T {
    allowScatterDepth.set(allowScatterDepth.get() + 1)
    try {
      return block()
    } finally {
      allowScatterDepth.set(allowScatterDepth.get() - 1)
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
      if (allowScatterDepth.get() > 0) sql.allowScatter() else sql,
      mapper,
      parameters,
      binders,
    )
}

internal fun String.allowScatter(): String {
  val keyword = requireNotNull(LEADING_SELECT.find(this)) { "ALLOW_SCATTER requires SQL beginning with SELECT" }
  return replaceRange(keyword.range, "${keyword.value} $ALLOW_SCATTER_HINT")
}

private const val ALLOW_SCATTER_HINT = "/*vt+ ALLOW_SCATTER */"
private val LEADING_SELECT = Regex("""^(\s*)SELECT\b""", RegexOption.IGNORE_CASE)
