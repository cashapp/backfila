package app.cash.backfila.client.misk.jooq

import app.cash.backfila.client.misk.BackfillModule
import app.cash.backfila.client.misk.jooq.internal.JooqBackend
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.Binder
import com.google.inject.BindingAnnotation
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import misk.inject.KAbstractModule
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * Installs the [BackfillBackend] for Hibernate backfills. See the java doc for [BackfillModule].
 */
class JooqBackfillModule<T : JooqBackfill<*, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    install(JooqBackfillBackendModule)
    // Ensures that the backfill class has an @Inject annotation and
    // that its dependencies are satisfied
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

private object JooqBackfillBackendModule : KAbstractModule() {
  override fun configure() {
    multibind<BackfillBackend>().to<JooqBackend>()
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
  binder,
  object : TypeLiteral<String>() {},
  object : TypeLiteral<KClass<out JooqBackfill<*, *>>>() {},
  ForBackfila::class.java
)

@BindingAnnotation
internal annotation class ForBackfila
