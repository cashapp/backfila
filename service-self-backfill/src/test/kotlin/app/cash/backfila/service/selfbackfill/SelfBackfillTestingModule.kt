package app.cash.backfila.service.selfbackfill

import app.cash.backfila.api.ServiceWebActionsModule
import app.cash.backfila.client.BackfilaCallbackConnectorProvider
import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.client.misk.MiskBackfillModule
import app.cash.backfila.client.misk.hibernate.HibernateBackfillModule
import app.cash.backfila.embedded.EmbeddedBackfilaModule
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfillRunListener
import app.cash.backfila.service.SlackHelper
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfilaPersistenceModule
import app.cash.backfila.service.runner.BackfillRunnerLoggingSetupProvider
import app.cash.backfila.service.runner.BackfillRunnerNoLoggingSetupProvider
import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.LogCollectorModule
import misk.scope.ActionScopedProviderModule
import wisp.deployment.TESTING

class SelfBackfillTestingModule : KAbstractModule() {
  override fun configure() {
    val config = BackfilaConfig(
      backfill_runner_threads = null,
      data_source_clusters = DataSourceClustersConfig(
        mapOf(
          "backfila-001" to DataSourceClusterConfig(
            writer = DataSourceConfig(
              type = DataSourceType.MYSQL,
              database = "backfila_selfbackfill_test",
              username = "root",
              migrations_resource = "classpath:/migrations",
            ),
            reader = null,
          ),
        ),
      ),
      web_url_root = "",
      slack = null,
    )
    newMultibinder<BackfillRunListener>()
    multibind<BackfillRunListener>().to<SlackHelper>()

    bind<BackfilaConfig>().toInstance(config)
    install(DeploymentModule(TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    install(HibernateTestingModule(BackfilaDb::class))
    install(BackfilaPersistenceModule(config))

    install(ServiceWebActionsModule())

//    bind(BackfilaClientServiceClientProvider::class.java)
//      .to(FakeBackfilaClientServiceClientProvider::class.java)

    bind(BackfillRunnerLoggingSetupProvider::class.java)
      .to(BackfillRunnerNoLoggingSetupProvider::class.java)

    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(MiskCaller::class)
      }
    },
    )

    install(EmbeddedBackfilaModule())
    install(
      MiskBackfillModule(
        BackfilaHttpClientConfig(
          url = "test.url",
          slack_channel = "#test",
        ),
      ),
    )

    bind(BackfilaClientLoggingSetupProvider::class.java)
      .to(BackfilaClientNoLoggingSetupProvider::class.java)

    newMapBinder<String, BackfilaCallbackConnectorProvider>(ForConnectors::class)
      .addBinding("ENVOY")
      .to(LocalCallbackConnectorProvider::class.java)

    install(HibernateBackfillModule.create<BackfillRegisteredParameters>())
  }
}
