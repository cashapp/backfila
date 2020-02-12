package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.embedded.EmbeddedBackfilaModule
import kotlin.reflect.KClass
import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.LogCollectorModule

internal class ClientMiskTestingModule(
  val useVitess: Boolean,
  val backfillClasses: List<KClass<out Backfill<*, *>>>
) : KAbstractModule() {
  override fun configure() {
    val dataSourceConfig = when {
      useVitess -> DataSourceConfig(
          type = DataSourceType.VITESS,
          username = "root",
          migrations_resource = "classpath:/schema",
          vitess_schema_resource_root = "classpath:/schema"
      )
      else -> DataSourceConfig(
          type = DataSourceType.MYSQL,
          database = "backfila_clientmiskservice_test",
          username = "root",
          migrations_resource = "classpath:/schema"
      )
    }
    install(HibernateModule(ClientMiskService::class, dataSourceConfig))
    install(object : HibernateEntityModule(ClientMiskService::class) {
      override fun configureHibernate() {
        addEntities(
            DbMenu::class,
            DbOrder::class,
            DbRestaurant::class
        )
      }
    })
    install(HibernateTestingModule(ClientMiskService::class))

    install(EnvironmentModule(Environment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(EmbeddedBackfilaModule())
    install(BackfilaModule(
        BackfilaClientConfig(
            url = "test.url", slack_channel = "#test"),
        backfillClasses
    ))
  }
}
