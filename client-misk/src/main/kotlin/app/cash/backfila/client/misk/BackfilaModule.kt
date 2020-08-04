package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.internal.BackfilaClient
import app.cash.backfila.client.misk.internal.BackfilaStartupConfigurator
import app.cash.backfila.client.misk.internal.RealBackfilaClient
import com.google.inject.Binder
import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import com.google.inject.multibindings.MapBinder
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.ServiceModule
import misk.inject.KAbstractModule

/**
 * Backfila-using applications install this and either [EmbeddedBackfilaModule] (testing and
 * development) or [BackfilaClientModule] (staging and production).
 */
class BackfilaModule(
  private val config: BackfilaClientConfig,
  @Deprecated(message = "Multibind backfills instead")
  private val backfills: List<KClass<out Backfill<*, *, *>>>? = null
) : KAbstractModule() {
  override fun configure() {
    bind<BackfilaClientConfig>().toInstance(config)

    bind<BackfilaClient>().to<RealBackfilaClient>()
    bind<BackfilaManagementClient>().to<RealBackfilaManagementClient>()

    mapBinder(binder())

    if (backfills != null) {
      for (backfill in backfills) {
        install(BackfillInstallModule.create(backfill))
      }
    }

    install(ServiceModule<BackfilaStartupConfigurator>())
  }

  @Singleton @Provides @ForBackfila fun provideMoshi() = Moshi.Builder()
      .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
      .build()
}

class BackfillInstallModule<T : Backfill<*, *, *>> private constructor(
  private val backfillClass: KClass<T>
) : KAbstractModule() {
  override fun configure() {
    mapBinder(binder()).addBinding(backfillClass.jvmName).toInstance(backfillClass)
  }

  companion object {
    inline fun <reified T : Backfill<*, *, *>> create(): BackfillInstallModule<T> = create(T::class)

    @JvmStatic
    fun <T : Backfill<*, *, *>> create(backfillClass: KClass<T>): BackfillInstallModule<T> {
      return BackfillInstallModule(backfillClass)
    }
  }
}

private fun mapBinder(binder: Binder) = MapBinder.newMapBinder(
    binder,
    object : TypeLiteral<String>() {},
    object : TypeLiteral<KClass<out Backfill<*, *, *>>>() {},
    ForBackfila::class.java
)

@BindingAnnotation
internal annotation class ForBackfila
