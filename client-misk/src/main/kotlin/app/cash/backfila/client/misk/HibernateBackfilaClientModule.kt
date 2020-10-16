package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.internal.BackfillOperator
import app.cash.backfila.client.misk.internal.HibernateBackfillOperator
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule

class HibernateBackfilaClientModule() : KAbstractModule() {
  override fun configure() {
    multibind<BackfillOperator.Backend>().to<HibernateBackfillOperator.HibernateBackend>()

    mapBinder(binder()) // Default the mapbinder to having no Backfills registered.
  }
}

class HibernateBackfillInstallModule<T : Backfill<*, *, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : Backfill<*, *, *>> create(): HibernateBackfillInstallModule<T> = create(T::class)

    @JvmStatic
    fun <T : Backfill<*, *, *>> create(backfillClass: KClass<T>): HibernateBackfillInstallModule<T> {
      return HibernateBackfillInstallModule(backfillClass)
    }
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
    binder,
    object : TypeLiteral<String>() {},
    object : TypeLiteral<KClass<out Backfill<*, *, *>>>() {},
    ForBackfila::class.java
)
