package app.cash.backfila.client.stat

import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import wisp.deployment.TESTING

/**
 * Simulates a specific service implementation module
 */
class TestingModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(BackfillsModule())

    install(EmbeddedBackfilaModule())
  }
}
