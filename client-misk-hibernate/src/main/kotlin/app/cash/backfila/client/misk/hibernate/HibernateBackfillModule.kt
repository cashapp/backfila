package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.RealBackfillModule
import app.cash.backfila.client.misk.hibernate.internal.HibernateBackend
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.inject.KAbstractModule

/**
 * Installs the [BackfillBackend] for Hibernate backfills. See the java doc for [RealBackfillModule].
 */
class HibernateBackfillModule<T : HibernateBackfill<*, *, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(HibernateBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
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

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object HibernateBackfillBackendModule : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(HibernateBackend::class.java)

    // Bind default primary key adapters
    install(PrimaryKeyCursorAdapterModule.create(IdPrimaryKeyCursorAdapter))
    install(PrimaryKeyCursorAdapterModule.create(StringPrimaryKeyCursorAdapter))
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out HibernateBackfill<*, *, *>>>() {},
  ForHibernateBackend::class.java
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForHibernateBackend
