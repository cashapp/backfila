package app.cash.backfila.client.sqldelight.internal

import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import app.cash.backfila.client.sqldelight.ForSqlDelightBackend
import app.cash.backfila.client.sqldelight.SqlDelightDatasourceBackfill
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class SqlDelightDatasourceBackend @Inject constructor(
  @ForSqlDelightBackend private val providers: Map<String, @JvmSuppressWildcards Provider<SqlDelightDatasourceBackfill<*, *, *>>>,
  @ForSqlDelightBackend private val registrations: Map<String, BackfillRegistration>,
) : BackfillBackend {
  override fun create(backfillName: String): BackfillOperator? {
    val backfill = providers[backfillName]?.get() ?: return null
    return createOperator(backfill)
  }

  @Suppress("UNCHECKED_CAST")
  private fun <K : Any, R : Any, P : Any> createOperator(
    backfill: SqlDelightDatasourceBackfill<K, R, P>,
  ) = SqlDelightDatasourceBackfillOperator(
    backfill,
    BackfilaParametersOperator(
      parametersClass(backfill::class),
    ) as BackfilaParametersOperator<P>,
  )

  private fun parametersClass(backfill: KClass<out SqlDelightDatasourceBackfill<*, *, *>>): KClass<*> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfill.java)

    // Like S3DatasourceBackfill<MyParameterClass>.
    val supertype = thisType.getSupertype(SqlDelightDatasourceBackfill::class.java).type as ParameterizedType

    // Like MyParameterClass
    return (Types.getRawType(supertype.actualTypeArguments[2])).kotlin
  }

  override fun backfills(): Set<BackfillRegistration> {
    return registrations.values.toSet()
  }
}
