package app.cash.backfila.client.misk

import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.hibernate.ChickenToBeefBackfill
import app.cash.backfila.client.misk.hibernate.HibernateBackfillModule
import app.cash.backfila.client.misk.hibernate.NullableParameterBackfill
import app.cash.backfila.client.misk.hibernate.RecordNoParametersConfigValuesBackfill
import app.cash.backfila.client.misk.hibernate.SinglePartitionHibernateTestBackfill
import app.cash.backfila.client.misk.hibernate.StringKeyBackfill
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcTestingModule
import misk.logging.LogCollectorModule

internal class ClientMiskTestingModule(
  private val useVitess: Boolean,
) : KAbstractModule() {
  override fun configure() {
    val dataSourceConfig = when {
      useVitess -> DataSourceConfig(
        type = DataSourceType.VITESS_MYSQL,
        username = "root",
        migrations_resource = "classpath:/schema",
        vitess_schema_resource_root = "classpath:/schema",
      )
      else -> DataSourceConfig(
        type = DataSourceType.MYSQL,
        database = "backfila_clientmiskservice_test",
        username = "root",
        migrations_resource = "classpath:/schema",
      )
    }
    install(HibernateModule(ClientMiskService::class, dataSourceConfig))
    install(object : HibernateEntityModule(ClientMiskService::class) {
      override fun configureHibernate() {
        addEntities(
          DbMenu::class,
          DbOrder::class,
          DbRestaurant::class,
        )
      }
    },
    )
    install(JdbcTestingModule(ClientMiskService::class))

    install(DeploymentModule(wisp.deployment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(EmbeddedBackfilaModule())
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url", slack_channel = "#test",
        ),
      ),
    )

    bind(BackfilaClientLoggingSetupProvider::class.java)
      .to(BackfilaClientNoLoggingSetupProvider::class.java)

    install(HibernateBackfillModule.create<SinglePartitionHibernateTestBackfill>())
    install(HibernateBackfillModule.create<ChickenToBeefBackfill>())
    install(HibernateBackfillModule.create<RecordNoParametersConfigValuesBackfill>())
    install(HibernateBackfillModule.create<StringKeyBackfill>())
    install(HibernateBackfillModule.create<NullableParameterBackfill>())
  }
}
