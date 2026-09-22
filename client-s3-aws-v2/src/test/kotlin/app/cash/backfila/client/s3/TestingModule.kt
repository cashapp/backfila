package app.cash.backfila.client.s3

import app.cash.backfila.client.s3.shim.FakeS3Module
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

    install(EmbeddedBackfilaModule())
    install(FakeS3Module())

    install(BackfillsModule())
  }
}
