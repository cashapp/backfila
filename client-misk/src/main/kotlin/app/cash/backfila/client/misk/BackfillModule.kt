package app.cash.backfila.client.misk

import app.cash.backfila.client.BackfilaManagementClient
import app.cash.backfila.client.RealBackfilaManagementClient
import app.cash.backfila.client.BackfilaClientConfig
import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.client.internal.EmptyBackend
import app.cash.backfila.client.internal.RealBackfilaClient
import app.cash.backfila.client.misk.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.misk.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.misk.internal.BackfilaStartupConfigurator
import app.cash.backfila.client.spi.BackfillBackend
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Backfila-using applications install at minimum 3 things.
 *  - This module
 *  - One or more specific client backend implementation modules such as `HibernateBackfillModule`
 *  - Either:
 *       [EmbeddedBackfilaModule] (testing and development)
 *       or [BackfilaClientModule] (staging and production).
 */
class BackfillModule @JvmOverloads constructor(
  private val config: BackfilaClientConfig,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
    BackfilaClientNoLoggingSetupProvider::class,
  private val dependsOn: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    bind<BackfilaClientConfig>().toInstance(config)

    bind<BackfilaClient>().to<RealBackfilaClient>()
    bind<BackfilaManagementClient>().to<RealBackfilaManagementClient>()
    bind<BackfilaClientLoggingSetupProvider>().to(loggingSetupProvider.java)

    install(
      ServiceModule(
        key = BackfilaStartupConfigurator::class.toKey(),
        dependsOn = dependsOn
      )
    )

    multibind<BackfillBackend>().to<EmptyBackend>()
  }

  @Singleton @Provides @ForBackfila fun provideMoshi(): Moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
    .build()
}
