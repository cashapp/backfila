package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.sqldelight.persistence.HockeyDataDb
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
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
    install(JdbcTestingModule(HockeyDataDb::class))

    install(EmbeddedBackfilaModule())

    install(TestBackfillsModule(useVitess, vitessPort))
  }
}
