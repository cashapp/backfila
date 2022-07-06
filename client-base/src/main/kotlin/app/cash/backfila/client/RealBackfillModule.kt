package app.cash.backfila.client

import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.client.internal.EmptyBackend
import app.cash.backfila.client.internal.RealBackfilaClient
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import javax.inject.Provider
import kotlin.reflect.KClass

class RealBackfillModule(
  private val configProvider: Provider<BackfilaClientConfig>,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
    BackfilaClientNoLoggingSetupProvider::class,
) : AbstractModule() {

  /**
   * This constructor is used for providing the config as an instance.
   */
  constructor(
    config: BackfilaClientConfig,
    loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class
  ) : this(Provider { config }, loggingSetupProvider)

  /**
   * This constructor is used for java land.
   */
  @Suppress("unused")
  @JvmOverloads
  constructor(
    configProvider: Provider<BackfilaClientConfig>,
    loggingSetupProvider: Class<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class.java
  ) : this(configProvider, loggingSetupProvider.kotlin)

  override fun configure() {
    bind(BackfilaClientConfig::class.java).toProvider(configProvider)

    bind(BackfilaClient::class.java).to(RealBackfilaClient::class.java)
    bind(BackfilaManagementClient::class.java).to(RealBackfilaManagementClient::class.java)
    bind(BackfilaClientLoggingSetupProvider::class.java).to(loggingSetupProvider.java)

    // Creates a multibinder for the backends that clients will install.
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(EmptyBackend::class.java)
  }
}
