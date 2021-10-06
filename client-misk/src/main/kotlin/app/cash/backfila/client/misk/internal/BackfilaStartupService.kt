package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.internal.BackfilaStartupConfigurator
import com.google.common.util.concurrent.AbstractIdleService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackfilaStartupService @Inject constructor(
  private val configurator: BackfilaStartupConfigurator,
) : AbstractIdleService() {
  override fun startUp() {
    configurator.sendBackfillMetadataToBackfila()
  }

  override fun shutDown() {
  }
}
