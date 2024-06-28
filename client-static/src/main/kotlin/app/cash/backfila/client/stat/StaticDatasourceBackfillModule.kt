package app.cash.backfila.client.stat

import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.stat.internal.StaticDatasourceBackend
import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Installs the [BackfillBackend] for Static Datasource backfills. See the java doc for [RealBackfillModule].
 */
class StaticDatasourceBackfillModule<T : StaticDatasourceBackfillBase<*, *>> private constructor(
  private val backfillClass: KClass<T>,
) : AbstractModule() {
  override fun configure() {
    install(StaticDatasourceBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : StaticDatasourceBackfillBase<*, *>> create(): StaticDatasourceBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : StaticDatasourceBackfillBase<*, *>> create(backfillClass: KClass<T>): StaticDatasourceBackfillModule<T> {
      return StaticDatasourceBackfillModule(backfillClass)
    }

    @JvmStatic
    fun <T : StaticDatasourceBackfillBase<*, *>> create(backfillClass: Class<T>): StaticDatasourceBackfillModule<T> {
      return StaticDatasourceBackfillModule(backfillClass.kotlin)
    }
  }
}

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object StaticDatasourceBackfillBackendModule : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(StaticDatasourceBackend::class.java)
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out StaticDatasourceBackfillBase<*, *>>>() {},
  ForStaticBackend::class.java,
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForStaticBackend
