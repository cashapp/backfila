package app.cash.backfila.client.backend

import app.cash.backfila.client.BackfilaClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

class NoBackfillsModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(wisp.deployment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    // We install the common backfila code but not any particular backend
    install(
      MiskBackfillModule(
        BackfilaClientConfig(
          url = "test.url", slack_channel = "#test"
        )
      )
    )

    install(EmbeddedBackfilaModule())
  }
}
