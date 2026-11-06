package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfilaPersistenceModule
import misk.MiskTestingServiceModule
import misk.audit.FakeAuditClientModule
import misk.config.AppNameModule
import misk.environment.DeploymentModule
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.WebServerTestingModule
import wisp.deployment.TESTING

@OptIn(ExperimentalBackfilaApi::class)
internal class BackfilaMcpTestingModule : KAbstractModule() {
  override fun configure() {
    val config = BackfilaConfig(
      backfill_runner_threads = null,
      data_source_clusters = DataSourceClustersConfig(
        mapOf(
          "backfila-001" to DataSourceClusterConfig(
            writer = DataSourceConfig(
              type = DataSourceType.MYSQL,
              database = "backfila_mcp_test",
              username = "root",
              migrations_resource = "classpath:/migrations",
            ),
            reader = null,
          ),
        ),
      ),
      web_url_root = "https://backfila.example.com/",
      slack = null,
    )
    bind<BackfilaConfig>().toInstance(config)

    install(AppNameModule("backfila"))
    install(FakeAuditClientModule())
    install(DeploymentModule(TESTING))
    install(MiskTestingServiceModule())
    install(WebServerTestingModule())

    install(HibernateTestingModule(BackfilaDb::class))
    install(BackfilaPersistenceModule(config))

    install(AccessControlModule())
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()

    install(BackfilaMcpModule())
  }
}
