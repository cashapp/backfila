package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.misk.client.BackfilaClientModule
import app.cash.backfila.client.misk.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.misk.embedded.EmbeddedBackfilaModule
import app.cash.backfila.client.misk.internal.BackfilaClient
import app.cash.backfila.client.misk.internal.BackfilaStartupConfigurator
import app.cash.backfila.client.misk.internal.RealBackfilaClient
import com.google.inject.BindingAnnotation
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Singleton
import kotlin.reflect.KClass
import misk.ServiceModule
import misk.inject.KAbstractModule

/**
 * Backfila-using applications install at minimum 3 things.
 *  - This module
 *  - One or more specific client backend implementation modules such as `HibernateBackfillModule`
 *  - Either:
 *       [EmbeddedBackfilaModule] (testing and development)
 *       or [BackfilaClientModule] (staging and production).
 */
class BackfillModule(
  private val config: BackfilaClientConfig,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class
) : KAbstractModule() {
  override fun configure() {
    bind<BackfilaClientConfig>().toInstance(config)

    bind<BackfilaClient>().to<RealBackfilaClient>()
    bind<BackfilaManagementClient>().to<RealBackfilaManagementClient>()
    bind<BackfilaClientLoggingSetupProvider>().to(loggingSetupProvider.java)

    install(ServiceModule<BackfilaStartupConfigurator>())
  }

  @Singleton @Provides @ForBackfila fun provideMoshi(): Moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
      .build()
}

@BindingAnnotation
internal annotation class ForBackfila
