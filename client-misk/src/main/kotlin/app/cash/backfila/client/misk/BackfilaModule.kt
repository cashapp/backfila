package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.internal.BackfilaClient
import app.cash.backfila.client.misk.internal.BackfilaStartupConfigurator
import app.cash.backfila.client.misk.internal.RealBackfilaClient
import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import com.squareup.moshi.Moshi
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.ServiceModule
import misk.inject.KAbstractModule
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Backfila-using applications install this and either [EmbeddedBackfilaModule] (testing and
 * development) or [RealBackfilaModule] (staging and production).
 */
class BackfilaModule(
  private val config: BackfilaClientConfig,
  private val backfills: List<KClass<out Backfill<*, *>>>
) : KAbstractModule() {
  override fun configure() {
    bind<BackfilaClientConfig>().toInstance(config)

    bind<BackfilaClient>().to<RealBackfilaClient>()

    val map = mutableMapOf<String, KClass<out Backfill<*, *>>>()
    for (backfill in backfills) {
      map[backfill.jvmName] = backfill
    }
    bind(object : TypeLiteral<Map<String, KClass<out Backfill<*, *>>>>() {})
        .annotatedWith(ForBackfila::class.java)
        .toInstance(map)

    install(ServiceModule<BackfilaStartupConfigurator>())
  }

  @Singleton @Provides @ForBackfila fun provideMoshi() = Moshi.Builder()
      .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
      .build()
}

@BindingAnnotation
internal annotation class ForBackfila
