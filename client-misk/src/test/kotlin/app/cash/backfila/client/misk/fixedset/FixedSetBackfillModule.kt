package app.cash.backfila.client.misk.fixedset

import app.cash.backfila.client.misk.ForBackfila
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule

class FixedSetBackfillModule<T : FixedSetBackfill<*>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(FixedSetBackfillBackendModule)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : FixedSetBackfill<*>> create(): FixedSetBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : FixedSetBackfill<*>> create(backfillClass: KClass<T>): FixedSetBackfillModule<T> {
      return FixedSetBackfillModule(backfillClass)
    }
  }
}

private object FixedSetBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    multibind<BackfillBackend>().to<FixedSetBackend>()
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out FixedSetBackfill<*>>>() {},
  ForBackfila::class.java
)
