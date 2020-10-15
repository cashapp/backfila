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
 * Backfila-using applications install this, one or more specific client implementation modules such
 * as [HibernateBackfilaClientModule] and either [EmbeddedBackfilaModule] (testing and development)
 * or [BackfilaClientModule] (staging and production).
 */
class BackfilaModule(
  private val config: BackfilaClientConfig,
  @Deprecated(message = "Multibind backfills using 'HibernateBackfillInstallModule.create' instead")
  private val backfills: List<KClass<out Backfill<*, *, *>>>? = null,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class
) : KAbstractModule() {
  override fun configure() {
    bind<BackfilaClientConfig>().toInstance(config)

    bind<BackfilaClient>().to<RealBackfilaClient>()
    bind<BackfilaManagementClient>().to<RealBackfilaManagementClient>()
    bind<BackfilaClientLoggingSetupProvider>().to(loggingSetupProvider.java)

    // For backwards compatibility for now we install the Hibernate Backfila Client implementation
    // and support the old backfills parameter.
    install(HibernateBackfilaClientModule())

    if (backfills != null) {
      for (backfill in backfills) {
        install(HibernateBackfillInstallModule.create(backfill))
      }
    }

    install(ServiceModule<BackfilaStartupConfigurator>())
  }

  @Singleton @Provides @ForBackfila fun provideMoshi(): Moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
      .build()
}

@BindingAnnotation
internal annotation class ForBackfila
