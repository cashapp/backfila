package app.cash.backfila.client.jooq

import app.cash.backfila.client.RealBackfillModule
import app.cash.backfila.client.jooq.internal.JooqBackend
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.AbstractModule
import com.google.inject.Binder
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.google.inject.multibindings.Multibinder
import javax.inject.Qualifier
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Installs the [BackfillBackend] for Hibernate backfills. See the java doc for [RealBackfillModule].
 */
class JooqBackfillModule<T : JooqBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>
) : AbstractModule() {
  override fun configure() {
    install(JooqBackfillBackendModule)
    // Ensures that the backfill class is injectable. If you are failing this check you probably
    // want to add an @Inject annotation to your class or check that all of your dependencies are provided.
    binder().getProvider(backfillClass.java)
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : JooqBackfill<*, *>> create(): JooqBackfillModule<T> = create(T::class)

    @JvmStatic
    fun <T : JooqBackfill<*, *>> create(backfillClass: KClass<T>): JooqBackfillModule<T> {
      return JooqBackfillModule(backfillClass)
    }
  }
}

/**
 * This is a kotlin object so these dependencies are only installed once.
 */
private object JooqBackfillBackendModule : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(JooqBackend::class.java)
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out JooqBackfill<*, *>>>() {},
  ForJooqBackend::class.java
)

/** Annotation for specifying dependencies specifically for this Backend. */
@Qualifier annotation class ForJooqBackend
