package app.cash.backfila.client.static

import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

/**
 * Simulates a specific service implementation module
 */
class TestingModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(wisp.deployment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(BackfillsModule())

    install(EmbeddedBackfilaModule())
  }
}
