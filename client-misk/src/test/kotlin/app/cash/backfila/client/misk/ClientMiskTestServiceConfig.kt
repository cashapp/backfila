package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.client.BackfilaClientModule
import app.cash.backfila.client.misk.hibernate.SinglePartitionHibernateTestBackfill
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.hibernate.Id
import misk.inject.KAbstractModule

// TODO(mikepaw) Not sure we even want this anymore. Maybe I'll replace this with an injector test of some kind?
class DummyBackfill : Backfill<DbMenu, Id<DbMenu>, NoParameters>() {
  override fun partitionProvider() = TODO()

  override fun backfillCriteria(config: BackfillConfig<NoParameters>) = TODO()
}

fun main(args: Array<String>) {
  MiskApplication(
      BackfilaModule(
          BackfilaClientConfig(
              url = "#test", slack_channel = "#test"
          )
      ),
      object : KAbstractModule() {
        override fun configure() {
          install(HibernateBackfillInstallModule.create<SinglePartitionHibernateTestBackfill>())
        }
      },
      BackfilaClientModule(),
      HttpClientsConfigModule(
          HttpClientsConfig(endpoints = mapOf(
              "backfila" to HttpClientEndpointConfig(url = "http://localhost:8080")))
      ),
      MiskRealServiceModule()
  ).run(args)
}
