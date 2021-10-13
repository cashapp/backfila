package app.cash.backfila.client.static

import app.cash.backfila.client.BackfilaClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import misk.inject.KAbstractModule

/**
 * Simulates a Backfills module where all the relevant backfills are registered.
 */
class BackfillsModule : KAbstractModule() {
  override fun configure() {
    install(
      MiskBackfillModule(
        BackfilaClientConfig(
          url = "test.url", slack_channel = "#test"
        )
      )
    )
    install(StaticDatasourceBackfillModule.create<StaticKotlinValBackfillTest.SaucesBackfill>())
  }
}
