package app.cash.backfila.client.misk.static

import app.cash.backfila.client.misk.spi.BackfillBackend
import app.cash.backfila.client.misk.static.internal.StaticDatasourceBackend
import com.google.inject.Binder
import com.google.inject.BindingAnnotation
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule

class StaticDatasourceBackfillModule<T : StaticDatasourceBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(StaticDatasourceBackfillBackendModule)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : StaticDatasourceBackfill<*, *>> create(): StaticDatasourceBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : StaticDatasourceBackfill<*, *>> create(backfillClass: KClass<T>): StaticDatasourceBackfillModule<T> {
      return StaticDatasourceBackfillModule(backfillClass)
    }
  }
}

private object StaticDatasourceBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    multibind<BackfillBackend>().to<StaticDatasourceBackend>()
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out StaticDatasourceBackfill<*, *>>>() {},
  ForBackfila::class.java
)

@BindingAnnotation
internal annotation class ForBackfila
