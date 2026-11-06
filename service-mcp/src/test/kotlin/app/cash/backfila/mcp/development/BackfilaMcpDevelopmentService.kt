package app.cash.backfila.mcp.development

import app.cash.backfila.client.BackfilaDefaultEndpointConfigModule
import app.cash.backfila.dashboard.ViewDashboardUrlProvider
import app.cash.backfila.dashboard.ViewLogsUrlProvider
import app.cash.backfila.mcp.BackfilaMcpModule
import app.cash.backfila.mcp.ExperimentalBackfilaApi
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfilaServiceModule
import app.cash.backfila.service.persistence.DbBackfillRun
import misk.MiskApplication
import misk.MiskCaller
import misk.MiskRealServiceModule
import misk.audit.NoOpAuditClientModule
import misk.environment.DeploymentModule
import misk.hibernate.Session
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.security.authz.AccessControlModule
import misk.security.authz.DevelopmentOnly
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.dashboard.AdminDashboardModule
import wisp.deployment.Deployment

/**
 * Runs Backfila locally with [BackfilaMcpModule] installed, serving MCP at
 * `http://localhost:8080/mcp`. It uses the same port and database as `BackfilaDevelopmentService`,
 * so the fake development services (mcdees, finedining) register with it the same way. Set
 * `BACKFILA_PORT` to run it next to an existing `BackfilaDevelopmentService`.
 *
 * Requests without an `X-Forwarded-User` header run as the development user `testfila`.
 */
@OptIn(ExperimentalBackfilaApi::class)
fun main(args: Array<String>) {
  val port = (System.getenv("BACKFILA_PORT") ?: "8080").toInt()
  val deployment = Deployment(name = "backfila", isLocalDevelopment = true)

  MiskApplication(
    object : KAbstractModule() {
      override fun configure() {
        install(MiskWebModule(WebConfig(port = port, idle_timeout = 500000, host = "0.0.0.0")))
        install(AccessControlModule())
        multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
        bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
          .toInstance(MiskCaller(user = "testfila", capabilities = setOf("users")))

        install(AdminDashboardModule(isDevelopment = true))
        bind<ViewLogsUrlProvider>().to<DevelopmentUrlProvider>()
        bind<ViewDashboardUrlProvider>().to<DevelopmentUrlProvider>()
      }
    },
    DeploymentModule(deployment),
    BackfilaServiceModule(
      deployment,
      BackfilaConfig(
        backfill_runner_threads = null,
        data_source_clusters = DataSourceClustersConfig(
          mapOf(
            "backfila-001" to DataSourceClusterConfig(
              writer = DataSourceConfig(
                type = DataSourceType.MYSQL,
                database = System.getenv("BACKFILA_DB_NAME") ?: "backfila_development",
                username = System.getenv("BACKFILA_DB_USER") ?: "root",
                migrations_resource = "classpath:/migrations",
                host = System.getenv("BACKFILA_DB_HOST") ?: "127.0.0.1",
                port = (System.getenv("BACKFILA_DB_PORT") ?: "3306").toInt(),
                password = System.getenv("BACKFILA_DB_PASSWORD"),
                connection_idle_timeout = null,
              ),
              reader = null,
            ),
          ),
        ),
        web_url_root = "http://localhost:$port/",
        slack = null,
      ),
    ),
    BackfilaMcpModule(),
    BackfilaDefaultEndpointConfigModule(),
    MiskRealServiceModule(),
    NoOpAuditClientModule(),
  ).run(args)
}

internal class DevelopmentUrlProvider : ViewLogsUrlProvider, ViewDashboardUrlProvider {
  override fun getUrl(session: Session, backfillRun: DbBackfillRun): String =
    "/${backfillRun.service.registry_name}/${backfillRun.service.variant}/${backfillRun.id}"
}
