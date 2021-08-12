package app.cash.backfila.client.misk

import app.cash.backfila.client.config.BackfilaClientConfig
import app.cash.backfila.client.misk.fixedset.FixedSetBackfillModule
import misk.inject.KAbstractModule

class BackfillsModule : KAbstractModule() {
  override fun configure() {
    install(
      BackfillModule(
        BackfilaClientConfig(
          url = "test.url", slack_channel = "#test"
        )
      )
    )
    install(FixedSetBackfillModule.create<FixedSetTest.ToUpperCaseBackfill>())
  }
}
