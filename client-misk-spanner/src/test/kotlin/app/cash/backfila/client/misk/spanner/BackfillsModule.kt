package app.cash.backfila.client.misk.spanner

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import misk.inject.KAbstractModule

class BackfillsModule : KAbstractModule() {
  override fun configure() {
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url", slack_channel = "#test"
        ),
        dependsOn = listOf()
      )
    )
    install(SpannerBackfillModule.create<SpannerBackfillTest.MakeTracksExplicitBackfill>())
  }
}
