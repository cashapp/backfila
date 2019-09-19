package app.cash.backfila.service

import com.google.inject.TypeLiteral
import misk.MiskApplication
import misk.MiskCaller
import misk.MiskRealServiceModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.scope.ActionScoped

fun main(args: Array<String>) {
  MiskApplication(
      object : KAbstractModule() {
        override fun configure() {
          val typeLiteral = object : TypeLiteral<ActionScoped<MiskCaller>>() {}
          bind(typeLiteral).toInstance(ActionScoped.of(
              MiskCaller(user = "development", capabilities = setOf("backfila--owners"))))
        }
      },
      BackfilaServiceModule(
          Environment.DEVELOPMENT,
          BackfilaConfig(
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
      MiskRealServiceModule()
  ).run(args)
}