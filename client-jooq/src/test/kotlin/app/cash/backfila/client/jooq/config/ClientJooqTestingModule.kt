package app.cash.backfila.client.jooq.config

import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import app.cash.backfila.client.jooq.JooqBackfillModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcModule
import misk.jdbc.JdbcTestingModule
import misk.jdbc.RealDatabasePool
import misk.logging.LogCollectorModule
import org.jooq.DSLContext
import org.jooq.Log
import org.jooq.SQLDialect
import org.jooq.conf.MappedSchema
import org.jooq.conf.RenderMapping
import org.jooq.conf.Settings
import org.jooq.impl.DSL
import org.jooq.impl.DefaultTransactionProvider
import org.jooq.tools.JooqLogger
import javax.inject.Provider
import javax.inject.Qualifier

class ClientJooqTestingModule : KAbstractModule() {
  override fun configure() {
    install(MiskTestingServiceModule())
    install(DeploymentModule(wisp.deployment.TESTING))
    val datasourceConfig = DataSourceConfig(
      type = DataSourceType.MYSQL,
      username = "root",
      password = "",
      database = "backfila_client_jooq_testing",
      migrations_resource = "classpath:/db-migrations"
    )
    install(
      JdbcModule(
        JooqDBIdentifier::class,
        datasourceConfig,
        null,
        null,
        RealDatabasePool
      )
    )
    val transacterKey = JooqTransacter::class.toKey(JooqDBIdentifier::class)
    val dataSourceServiceProvider = getProvider(keyOf<DataSourceService>(JooqDBIdentifier::class))
    bind(transacterKey).toProvider(
      Provider {
        JooqTransacter(
          dslContext = dslContext(dataSourceServiceProvider.get(), datasourceConfig)
        )
      }
    ).asSingleton()
    install(JdbcTestingModule(JooqDBIdentifier::class))
    install(LogCollectorModule())

    registerBackfills()
    JooqLogger.globalThreshold(Log.Level.DEBUG)
//    LoggerFactory.getLogger("org.jooq.tools.LoggerListener").
  }

  /**
   * This is how you need to set up jooq backfills in your project
   */
  private fun registerBackfills() {
    // Use the real backfill `BackfilaClientModule` for production and
    // `EmbeddedBackfilaModule` for tests
    install(EmbeddedBackfilaModule())
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url", slack_channel = "#test"
        )
      )
    )
    // Use `BackfilaClientMDCLoggingSetupProvider` for production and
    // `BackfilaClientNoLoggingSetupProvider` for tests
    bind(BackfilaClientLoggingSetupProvider::class.java)
      .to(BackfilaClientNoLoggingSetupProvider::class.java)

    // Registers all your backfills here
    install(JooqBackfillModule.create<JooqMenuTestBackfill>())
    install(JooqBackfillModule.create<JooqWidgetCompoundKeyBackfill>())
  }

  private fun dslContext(
    dataSourceService: DataSourceService,
    datasourceConfig: DataSourceConfig
  ): DSLContext {
    val settings = Settings()
      .withExecuteWithOptimisticLocking(true)
      .withRenderMapping(
        RenderMapping().withSchemata(
          MappedSchema()
            .withInput("jooq")
            .withOutput(datasourceConfig.database)
        )
      )
    return DSL.using(dataSourceService.get(), SQLDialect.MYSQL, settings).apply {
      configuration().set(
        DefaultTransactionProvider(
          configuration().connectionProvider(),
          false
        )
      ).set(JooqSQLLogger())
    }
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqDBIdentifier
