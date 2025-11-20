package app.cash.backfila.client.s3

import app.cash.backfila.client.s3.shim.FakeS3V2Module
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

/**
 * Simulates a specific service implementation module using V2.
 */
class TestingModuleV2 : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(wisp.deployment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    install(EmbeddedBackfilaModule())
    install(FakeS3V2Module())

    install(BackfillsModuleV2())
  }
}
