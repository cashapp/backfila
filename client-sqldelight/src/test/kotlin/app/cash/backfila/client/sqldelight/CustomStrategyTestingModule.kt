package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer
import app.cash.backfila.client.sqldelight.persistence.HockeyDataDb
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.google.inject.Provides
import java.sql.Connection
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
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
 * Testing module for custom strategy backfill tests.
 */
class CustomStrategyTestingModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(JdbcTestingModule(HockeyDataDb::class))

    install(
      JdbcModule(
        HockeyDataDb::class,
        DataSourceConfig(
          type = DataSourceType.MYSQL,
          username = "root",
          password = "",
          database = "hockeydata_testing",
          migrations_resource = "classpath:/migrations",
        ),
      ),
    )

    install(EmbeddedBackfilaModule())

    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url", slack_channel = "#test",
        ),
      ),
    )

    // Register the custom strategy backfill
    install(SqlDelightDatasourceBackfillModule.create<CustomStrategyBackfill>())
  }

  @Provides
  @Singleton
  fun provideHockeyDatabase(
    @HockeyDataDb dataSource: Provider<DataSource>,
  ): HockeyDataDatabase {
    val driver = object : JdbcDriver() {
      override fun getConnection(): Connection {
        val connection = dataSource.get().connection
        connection.autoCommit = true
        return connection
      }

      override fun notifyListeners(vararg queryKeys: String) {
        // No-op. JDBC Driver is not set up for observing queries by default.
      }

      override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        // No-op. JDBC Driver is not set up for observing queries by default.
      }

      override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        // No-op. JDBC Driver is not set up for observing queries by default.
      }

      override fun closeConnection(connection: Connection) {
        connection.close()
      }
    }

    return HockeyDataDatabase(
      driver = driver,
      hockeyPlayerAdapter = HockeyPlayer.Adapter(
        positionAdapter = EnumColumnAdapter(),
        shootsAdapter = EnumColumnAdapter(),
      ),
    )
  }
}
