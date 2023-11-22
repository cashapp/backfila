package app.cash.backfila.client

import app.cash.backfila.client.fixedset.FixedSetBackfillModule
import app.cash.backfila.client.misk.MiskBackfillModule
import misk.inject.KAbstractModule

class BackfillsModule : KAbstractModule() {
  override fun configure() {
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url",
          slack_channel = "#test",
        ),
      ),
    )
    install(FixedSetBackfillModule.create<ToUpperCaseBackfill>())
    install(FixedSetBackfillModule.create<ToLowerCaseBackfill>())
    install(FixedSetBackfillModule.create<DeleteBackfillByTest.TenYearBackfill>())
    install(FixedSetBackfillModule.create<DeleteBackfillByTest.DeprecatedBackfill>())
    install(FixedSetBackfillModule.create<PrepareWithParametersTest.PrepareWithParametersBackfill>())
  }
}
