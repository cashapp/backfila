package app.cash.backfila.development.mcdees

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.misk.client.BackfilaMiskClientModule
import app.cash.backfila.client.stat.StaticDatasourceBackfillModule
import misk.inject.KAbstractModule

internal class McDeesServiceModule(
  private val variant: String,
  private val port: Int,
) : KAbstractModule() {
  override fun configure() {
    // Development Service Config

    // Backfill Config
    install(BackfilaMiskClientModule())
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "http://localhost:$port/",
          slack_channel = "#test",
          variant = variant,
        ),
      ),
    )
    install(StaticDatasourceBackfillModule.create<BurgerFlippingBackfill>())
    install(StaticDatasourceBackfillModule.create<BootsAndCatsBackfill>())
  }
}
