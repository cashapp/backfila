package app.cash.backfila.client

import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.client.internal.EmptyBackend
import app.cash.backfila.client.internal.RealBackfilaClient
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import kotlin.reflect.KClass

class RealBackfillModule(
  private val config: BackfilaClientConfig,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
    BackfilaClientNoLoggingSetupProvider::class,
) : AbstractModule() {

  /**
   * This constructor is used for java land.
   */
  @Suppress("unused")
  @JvmOverloads
  constructor(
    config: BackfilaClientConfig,
    loggingSetupProvider: Class<out BackfilaClientLoggingSetupProvider> =
        BackfilaClientNoLoggingSetupProvider::class.java
  ) : this(config, loggingSetupProvider.kotlin)

  override fun configure() {
    bind(BackfilaClientConfig::class.java).toInstance(config)

    bind(BackfilaClient::class.java).to(RealBackfilaClient::class.java)
    bind(BackfilaManagementClient::class.java).to(RealBackfilaManagementClient::class.java)
    bind(BackfilaClientLoggingSetupProvider::class.java).to(loggingSetupProvider.java)

    // Creates a multibinder for the backends that clients will install.
    Multibinder.newSetBinder(binder(), BackfillBackend::class.java).addBinding()
      .to(EmptyBackend::class.java)
  }
}
