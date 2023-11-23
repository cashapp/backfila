package app.cash.backfila.development.finedining

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.misk.client.BackfilaMiskClientModule
import app.cash.backfila.client.stat.StaticDatasourceBackfillModule
import misk.inject.KAbstractModule

internal class FineDiningServiceModule : KAbstractModule() {
  override fun configure() {
    install(BackfilaMiskClientModule())
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "http://localhost:8081/",
          slack_channel = "#test",
        ),
      ),
    )
    install(StaticDatasourceBackfillModule.create<SlowMealsBackfill>())
  }
}
