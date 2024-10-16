package app.cash.backfila.client.sqldelight.internal

import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.Description
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.spi.BackfilaParametersOperator
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import app.cash.backfila.client.sqldelight.ForSqlDelightBackend
import app.cash.backfila.client.sqldelight.SqlDelightDatasourceBackfill
import app.cash.backfila.client.sqldelight.SqlDelightRecordSource
import com.google.inject.Injector
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

@Singleton
class SqlDelightDatasourceBackend @Inject constructor(
  private val injector: Injector,
  @ForSqlDelightBackend private val backfills: MutableMap<String, KClass<out SqlDelightDatasourceBackfill<*, *, *, *>>>,
) : BackfillBackend {

  /** Creates Backfill instances. Each backfill ID gets a new Backfill instance. */
  private fun getBackfill(name: String): SqlDelightDatasourceBackfill<*, *, *, *>? {
    val backfillClass = backfills[name]
    return backfillClass?.let {
      injector.getInstance(backfillClass.java) as SqlDelightDatasourceBackfill<*, *, *, *>
    }
  }

  private fun <S : SqlDelightRecordSource<K, R>, K : Any, R : Any, Param : Any> createSqlDelightDatasourceOperator(
    backfill: SqlDelightDatasourceBackfill<S, K, R, Param>,
  ) = SqlDelightDatasourceBackfillOperator(
    backfill,
    BackfilaParametersOperator(parametersClass(backfill::class)),
  )

  override fun create(backfillName: String): BackfillOperator? {
    return getBackfill(backfillName)?.let { backfill ->
      @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
      return createSqlDelightDatasourceOperator(backfill as SqlDelightDatasourceBackfill<SqlDelightRecordSource<Any, Any>, Any, Any, Any>)
    }
  }

  override fun backfills(): Set<BackfillRegistration> {
    return backfills.map {
      BackfillRegistration(
        name = it.key,
        description = it.value.findAnnotation<Description>()?.text,
        parametersClass = parametersClass(it.value as KClass<SqlDelightDatasourceBackfill<SqlDelightRecordSource<Any, Any>, Any, Any, Any>>),
        deleteBy = it.value.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
      )
    }.toSet()
  }

  private fun <S : SqlDelightRecordSource<K, R>, K : Any, R : Any, P : Any> parametersClass(backfillClass: KClass<out SqlDelightDatasourceBackfill<S, K, R, P>>): KClass<P> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like S3DatasourceBackfill<MyParameterClass>.
    val supertype = thisType.getSupertype(
      SqlDelightDatasourceBackfill::class.java,
    ).type as ParameterizedType

    // Like MyParameterClass
    return (Types.getRawType(supertype.actualTypeArguments[3]) as Class<P>).kotlin
  }
}
