package app.cash.backfila.client

import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.client.internal.EmptyBackend
import app.cash.backfila.client.internal.RealBackfilaClient
import app.cash.backfila.client.spi.BackfillBackend
import com.google.inject.AbstractModule
import com.google.inject.Provider
import com.google.inject.multibindings.Multibinder
import com.google.inject.util.Providers
import jakarta.inject.Provider as JakartaProvider
import javax.inject.Provider as JavaxProvider
import kotlin.reflect.KClass

class RealBackfillModule
@Deprecated("Providing an instance is preferred over a provider", replaceWith = ReplaceWith("RealBackfillModule(config,loggingSetupProvider)"))
constructor(
  private val configProvider: Provider<BackfilaClientConfig>,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
    BackfilaClientNoLoggingSetupProvider::class,
) : AbstractModule() {

  /**
   * This constructor is the preferred constructor for RealBackfillModule
   */
  @JvmOverloads
  constructor(
    config: BackfilaClientConfig,
    loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class,
  ) : this(Provider { config }, loggingSetupProvider)

  /**
   * This constructor preserves the Guice 6 / javax.inject Kotlin call site.
   */
  @Suppress("unused")
  @Deprecated("Providing an instance is preferred over a provider", replaceWith = ReplaceWith("RealBackfillModule(config,loggingSetupProvider)"))
  constructor(
    configProvider: JavaxProvider<BackfilaClientConfig>,
    loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class,
  ) : this(Providers.guicify(configProvider), loggingSetupProvider)

  /**
   * This constructor is used for java land.
   */
  @Suppress("unused")
  @JvmOverloads
  @Deprecated("Providing an instance is preferred over a provider", replaceWith = ReplaceWith("RealBackfillModule(config,loggingSetupProvider)"))
  constructor(
    configProvider: JavaxProvider<BackfilaClientConfig>,
    loggingSetupProvider: Class<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class.java,
  ) : this(Providers.guicify(configProvider), loggingSetupProvider.kotlin)

  /**
   * This constructor preserves the Guice 7 / jakarta.inject Kotlin call site.
   */
  @Suppress("unused")
  @Deprecated("Providing an instance is preferred over a provider", replaceWith = ReplaceWith("RealBackfillModule(config,loggingSetupProvider)"))
  constructor(
    configProvider: JakartaProvider<BackfilaClientConfig>,
    loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider>,
  ) : this(Providers.guicify(configProvider), loggingSetupProvider)

  /**
   * This constructor is used for guice 7 compatibility.
   */
  @Suppress("unused")
  @JvmOverloads
  @Deprecated("Providing an instance is preferred over a provider", replaceWith = ReplaceWith("RealBackfillModule(config,loggingSetupProvider)"))
  constructor(
    configProvider: JakartaProvider<BackfilaClientConfig>,
    loggingSetupProvider: Class<out BackfilaClientLoggingSetupProvider> =
      BackfilaClientNoLoggingSetupProvider::class.java,
  ) : this(Providers.guicify(configProvider), loggingSetupProvider.kotlin)

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
