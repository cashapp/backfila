package app.cash.backfila.client.sqldelight

/** Intercepts execution of queries owned by a [SqlDelightDatasourceBackfill]. */
interface SqlDelightQueryInterceptor {
  fun <T> intercept(block: () -> T): T

  companion object {
    @JvmField
    val NONE: SqlDelightQueryInterceptor =
      object : SqlDelightQueryInterceptor {
        override fun <T> intercept(block: () -> T): T = block()
      }
  }
}
