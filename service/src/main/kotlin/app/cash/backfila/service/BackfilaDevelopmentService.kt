package app.cash.backfila.service

import app.cash.backfila.client.BackfilaDefaultEndpointConfigModule
import app.cash.backfila.dashboard.ViewLogsUrlProvider
import misk.MiskApplication
import misk.MiskCaller
import misk.MiskRealServiceModule
import misk.environment.Deployment
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.environment.Environment
import misk.hibernate.Session
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.security.authz.DevelopmentOnly
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.dashboard.AdminDashboardModule

fun main(args: Array<String>) {
  val env = Env(Environment.rawEnvironment())
  val deployment = Deployment(name = "backfila", isLocalDevelopment = true)

  MiskApplication(
      object : KAbstractModule() {
        override fun configure() {
          val webConfig = WebConfig(
              port = 8080,
              idle_timeout = 500000,
              host = "127.0.0.1"
          )
          install(MiskWebModule(webConfig))
          multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
          bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
              .toInstance(MiskCaller(user = "testfila"))
          bind<ViewLogsUrlProvider>().to<DevelopmentViewLogsUrlProvider>()
        }
      },
      DeploymentModule(deployment, env),
      BackfilaServiceModule(
          deployment,
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
      AdminDashboardModule(isDevelopment = true),
      BackfilaDefaultEndpointConfigModule(),
      MiskRealServiceModule()
  ).run(args)
}

class DevelopmentViewLogsUrlProvider : ViewLogsUrlProvider {
  override fun getUrl(session: Session, backfillRun: DbBackfillRun): String {
    return "/"
  }
}
