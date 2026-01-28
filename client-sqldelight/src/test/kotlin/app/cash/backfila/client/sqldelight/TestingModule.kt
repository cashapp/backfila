package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.persistence.HockeyDataDb
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcModule
import misk.jdbc.JdbcTestingModule
import misk.logging.LogCollectorModule
import wisp.deployment.TESTING

/**
 * Simulates a specific service implementation module
 */
class TestingModule(
  private val useVitess: Boolean = false,
  private val vitessPort: Int = 27003,
) : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    val dataSourceConfig = if (useVitess) {
      DataSourceConfig(
        type = DataSourceType.VITESS_MYSQL,
        username = "root",
        port = vitessPort,
        vitess_schema_resource_root = "classpath:/vitess/schema",
      )
    } else {
      DataSourceConfig(
        type = DataSourceType.MYSQL,
        username = "root",
        password = "",
        database = "hockeydata_testing",
        migrations_resource = "classpath:/migrations",
      )
    }
    install(JdbcModule(HockeyDataDb::class, dataSourceConfig))
    install(JdbcTestingModule(HockeyDataDb::class))

    install(EmbeddedBackfilaModule())

    install(TestBackfillsModule(useVitess, vitessPort))
  }
}
