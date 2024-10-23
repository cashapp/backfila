package app.cash.backfila.client.sqldelight.internal

import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import app.cash.backfila.client.sqldelight.ForSqlDelightBackend
import app.cash.backfila.client.sqldelight.SqlDelightDatasourceBackfill
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class SqlDelightDatasourceBackend @Inject constructor(
  @ForSqlDelightBackend private val providers: Map<String, @JvmSuppressWildcards Provider<SqlDelightDatasourceBackfill<*, *, *>>>,
  @ForSqlDelightBackend private val registrations: Map<String, BackfillRegistration>,
) : BackfillBackend {
  override fun create(backfillName: String): BackfillOperator? {
    val parametersClass = registrations[backfillName]?.parametersClass ?: return null
    val backfill = providers[backfillName]?.get() ?: return null

    @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
    backfill as SqlDelightDatasourceBackfill<Any, Any, Any>

    return SqlDelightDatasourceBackfillOperator(
      backfill,
      BackfilaParametersOperator(parametersClass),
    )
  }

  override fun backfills(): Set<BackfillRegistration> {
    return registrations.values.toSet()
  }
}
