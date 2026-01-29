package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.sqldelight.SqlDelightDatasourceBackfillModule.Companion.create
import app.cash.backfila.client.sqldelight.hockeydata.HockeyDataDatabase
import app.cash.backfila.client.sqldelight.hockeydata.HockeyPlayer
import app.cash.backfila.client.sqldelight.persistence.HockeyDataDb
import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.Query
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import com.google.inject.Provides
import java.sql.Connection
import javax.inject.Provider
import javax.inject.Singleton
import javax.sql.DataSource
import misk.inject.KAbstractModule

/**
 * Simulates a Backfills module where all the relevant backfills are registered.
 */
class TestBackfillsModule(
  private val useVitess: Boolean = false,
  private val vitessPort: Int = 27003,
) : KAbstractModule() {
  override fun configure() {
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url", slack_channel = "#test",
        ),
      ),
    )
    install(create<PlayerOriginBackfill>())
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
