package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

class TestingModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule.forTesting())
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(BackfillsModule())

    install(EmbeddedBackfilaModule())
  }
}
