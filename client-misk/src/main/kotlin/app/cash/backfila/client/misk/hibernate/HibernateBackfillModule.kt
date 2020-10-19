package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.BackfillModule
import app.cash.backfila.client.misk.ForBackfila
import app.cash.backfila.client.misk.hibernate.internal.HibernateBackend
import app.cash.backfila.client.misk.spi.BackfillBackend
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule

/**
 * Installs the [BackfillBackend] for Hibernate backfills. See the java doc for [BackfillModule].
 */
class HibernateBackfillModule<T : HibernateBackfill<*, *, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(HibernateBackfillBackendModule)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : HibernateBackfill<*, *, *>> create(): HibernateBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : HibernateBackfill<*, *, *>> create(backfillClass: KClass<T>): HibernateBackfillModule<T> {
      return HibernateBackfillModule(backfillClass)
    }
  }
}

private object HibernateBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    multibind<BackfillBackend>().to<HibernateBackend>()
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
    binder,
    object : TypeLiteral<String>() {},
    object : TypeLiteral<KClass<out HibernateBackfill<*, *, *>>>() {},
    ForBackfila::class.java
)
