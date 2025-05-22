package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.BackfillUnit
import app.cash.backfila.client.DeleteBy
import app.cash.backfila.client.Description
import app.cash.backfila.client.parseDeleteByDate
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillRegistration
import app.cash.backfila.client.sqldelight.internal.SqlDelightDatasourceBackend
import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmName

/**
 * Installs the [BackfillBackend] for SqlDelight Datasource backfills. See the documentation for [RealBackfillModule].
 */
class SqlDelightDatasourceBackfillModule<T : SqlDelightDatasourceBackfill<*, *, *>> private constructor(
  private val backfillClass: KClass<T>,
) : AbstractModule() {
  private val registration = BackfillRegistration(
    name = backfillClass.jvmName,
    description = backfillClass.findAnnotation<Description>()?.text,
    parametersClass = parametersClass(),
    deleteBy = backfillClass.findAnnotation<DeleteBy>()?.parseDeleteByDate(),
    unit = BackfillUnit.RECORDS.displayName,
  )

  override fun configure() {
    install(SqlDelightDatasourceBackfillBackendModule)
    backfillBinder().addBinding(backfillClass.jvmName).to(backfillClass.java as Class<SqlDelightDatasourceBackfill<*, *, *>>)
    backfillRegistrationBinder().addBinding(backfillClass.jvmName).toInstance(registration)
  }

  private fun parametersClass(): KClass<Any> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(backfillClass.java)

    // Like S3DatasourceBackfill<MyParameterClass>.
    val supertype = thisType.getSupertype(
      SqlDelightDatasourceBackfill::class.java,
    ).type as ParameterizedType

    // Like MyParameterClass
    return (Types.getRawType(supertype.actualTypeArguments[2]) as Class<Any>).kotlin
  }

  companion object {
    inline fun <reified T : SqlDelightDatasourceBackfill<*, *, *>> create(): SqlDelightDatasourceBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : SqlDelightDatasourceBackfill<*, *, *>> create(backfillClass: KClass<T>): SqlDelightDatasourceBackfillModule<T> {
      return SqlDelightDatasourceBackfillModule(backfillClass)
    }

    @JvmStatic
    fun <T : SqlDelightDatasourceBackfill<*, *, *>> create(backfillClass: Class<T>): SqlDelightDatasourceBackfillModule<T> {
      return SqlDelightDatasourceBackfillModule(backfillClass.kotlin)
    }
  }

  private fun backfillBinder() = MapBinder.newMapBinder(
    binder(),
    object : TypeLiteral<String>() {},
    object : TypeLiteral<SqlDelightDatasourceBackfill<*, *, *>>() {},
    ForSqlDelightBackend::class.java,
  )

  private fun backfillRegistrationBinder() = MapBinder.newMapBinder(
    binder(),
    String::class.java,
    BackfillRegistration::class.java,
    ForSqlDelightBackend::class.java,
  )
}

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object SqlDelightDatasourceBackfillBackendModule : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(SqlDelightDatasourceBackend::class.java)
  }
}

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForSqlDelightBackend
