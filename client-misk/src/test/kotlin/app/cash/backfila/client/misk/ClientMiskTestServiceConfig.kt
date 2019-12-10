package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.client.BackfilaClientModule
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.hibernate.Id

// TODO(mikepaw) Not sure we even want this anymore. Maybe I'll replace this with an injector test of some kind?
class DummyBackfill : Backfill<DbMenu, Id<DbMenu>>() {
  override fun instanceProvider() = TODO()

  override fun backfillCriteria(config: BackfillConfig) = TODO()
}

fun main(args: Array<String>) {
  MiskApplication(
      BackfilaModule(
          BackfilaClientConfig(
              url = "#test", slack_channel = "#test"),
          listOf(DummyBackfill::class)
      ),
      BackfilaClientModule(),
      HttpClientsConfigModule(
          HttpClientsConfig(endpoints = mapOf(
              "backfila" to HttpClientEndpointConfig(url = "http://localhost:8080")))
      ),
      MiskRealServiceModule()
  ).run(args)
}
