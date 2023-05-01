package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.sqldelight.internal.SqlDelightDatasourceBackend
import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Installs the [BackfillBackend] for SqlDelight Datasource backfills. See the java doc for [RealBackfillModule].
 */
class SqlDelightDatasourceBackfillModule<T : SqlDelightDatasourceBackfill<*, *, *, *>> private constructor(
  private val backfillClass: KClass<T>,
) : AbstractModule() {
  override fun configure() {
    install(SqlDelightDatasourceBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : SqlDelightDatasourceBackfill<*, *, *, *>> create(): SqlDelightDatasourceBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : SqlDelightDatasourceBackfill<*, *, *, *>> create(backfillClass: KClass<T>): SqlDelightDatasourceBackfillModule<T> {
      return SqlDelightDatasourceBackfillModule(backfillClass)
    }

    @JvmStatic
    fun <T : SqlDelightDatasourceBackfill<*, *, *, *>> create(backfillClass: Class<T>): SqlDelightDatasourceBackfillModule<T> {
      return SqlDelightDatasourceBackfillModule(backfillClass.kotlin)
    }
  }
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

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out SqlDelightDatasourceBackfill<*, *, *, *>>>() {},
  ForSqlDelightBackend::class.java,
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForSqlDelightBackend
