package app.cash.backfila.service

import app.cash.backfila.client.BackfilaDefaultEndpointConfigModule
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.dashboard.AdminDashboardModule

fun main(args: Array<String>) {
  MiskApplication(
      object : KAbstractModule() {
        override fun configure() {
          val webConfig = WebConfig(
              port = 8080,
              idle_timeout = 500000,
              host = "127.0.0.1"
          )
          install(MiskWebModule(webConfig))
        }
      },
      BackfilaServiceModule(
          Environment.DEVELOPMENT,
          BackfilaConfig(
              backfill_runner_threads = null,
              data_source_clusters = DataSourceClustersConfig(
                  mapOf("backfila-001" to DataSourceClusterConfig(
                      writer = DataSourceConfig(
                          type = DataSourceType.MYSQL,
                          database = "backfila_development",
                          username = "root",
                          migrations_resource = "classpath:/migrations"
                      ),
                      reader = null
                  ))
              ),
              web_url_root = "http://localhost:8080/app/",
              slack = null
          )
      ),
      AdminDashboardModule(Environment.DEVELOPMENT),
      BackfilaDefaultEndpointConfigModule(),
      MiskRealServiceModule()
  ).run(args)
}
