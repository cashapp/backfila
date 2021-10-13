package app.cash.backfila.client.misk

import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.RealBackfillModule
import app.cash.backfila.client.misk.internal.BackfilaStartupService
import com.google.common.util.concurrent.Service
import com.google.inject.Key
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.inject.toKey
import kotlin.reflect.KClass

/**
 * Backfila-using applications install at minimum 3 things.
 *  - This module
 *  - One or more specific client backend implementation modules such as `HibernateBackfillModule`
 *  - Either:
 *       [EmbeddedBackfilaModule] (testing and development)
 *       or [BackfilaClientModule] (staging and production).
 */
class MiskBackfillModule @JvmOverloads constructor(
  private val config: BackfilaHttpClientConfig,
  private val loggingSetupProvider: KClass<out BackfilaClientLoggingSetupProvider> =
    BackfilaClientNoLoggingSetupProvider::class,
  private val dependsOn: List<Key<out Service>> = listOf()
) : KAbstractModule() {
  override fun configure() {
    install(RealBackfillModule(config.toBackfilaClientConfig(), loggingSetupProvider))

    install(
      ServiceModule(
        key = BackfilaStartupService::class.toKey(),
        dependsOn = dependsOn
      )
    )
  }
}
