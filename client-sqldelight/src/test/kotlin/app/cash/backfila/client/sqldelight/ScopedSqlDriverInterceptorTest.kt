package app.cash.backfila.client.sqldelight

import app.cash.sqldelight.Query
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ScopedSqlDriverInterceptorTest {
  @Test
  fun `transforms SQL only within intercept scope`() {
    val delegate = RecordingSqlDriver()
    val driver = ScopedSqlDriverInterceptor(delegate) { sql -> "$sql /* transformed */" }

    driver.executeQuery(null, "SELECT 1", { QueryResult.Value(Unit) }, 0, null)
    driver.intercept {
      driver.executeQuery(null, "SELECT 2", { QueryResult.Value(Unit) }, 0, null)
      driver.intercept {
        driver.executeQuery(null, "SELECT 3", { QueryResult.Value(Unit) }, 0, null)
      }
    }
    driver.executeQuery(null, "SELECT 4", { QueryResult.Value(Unit) }, 0, null)

    assertThat(delegate.sql).containsExactly(
      "SELECT 1",
      "SELECT 2 /* transformed */",
      "SELECT 3 /* transformed */",
      "SELECT 4",
    )
  }

  private class RecordingSqlDriver : SqlDriver {
    val sql = mutableListOf<String>()

    override fun <R> executeQuery(
      identifier: Int?,
      sql: String,
      mapper: (SqlCursor) -> QueryResult<R>,
      parameters: Int,
      binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<R> {
      this.sql += sql
      @Suppress("UNCHECKED_CAST")
      return QueryResult.Value(Unit as R)
    }

    override fun execute(
      identifier: Int?,
      sql: String,
      parameters: Int,
      binders: (SqlPreparedStatement.() -> Unit)?,
    ): QueryResult<Long> = error("unused")

    override fun newTransaction(): QueryResult<Transacter.Transaction> = error("unused")

    override fun currentTransaction(): Transacter.Transaction? = null

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit

    override fun close() = Unit
  }
}
