package app.cash.backfila

import app.cash.backfila.api.ServiceWebActionsModule
import app.cash.backfila.client.BackfilaCallbackConnectorProvider
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaCallbackConnectorProvider
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfillRunListener
import app.cash.backfila.service.SlackHelper
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfilaPersistenceModule
import app.cash.backfila.service.runner.BackfillRunnerLoggingSetupProvider
import app.cash.backfila.service.runner.BackfillRunnerNoLoggingSetupProvider
import app.cash.backfila.service.scheduler.ForBackfilaScheduler
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import java.util.concurrent.Executors
import javax.inject.Singleton
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

internal class BackfilaTestingModule : KAbstractModule() {
  override fun configure() {
    val config = BackfilaConfig(
      backfill_runner_threads = null,
      data_source_clusters = DataSourceClustersConfig(
        mapOf(
          "backfila-001" to DataSourceClusterConfig(
            writer = DataSourceConfig(
              type = DataSourceType.MYSQL,
              database = "backfila_test",
              username = "root",
              migrations_resource = "classpath:/migrations",
              show_sql = "true",
            ),
            reader = null,
          ),
        ),
      ),
      web_url_root = "",
      slack = null,
    )
    bind<BackfilaConfig>().toInstance(config)

    newMultibinder<BackfillRunListener>()
      .addBinding()
      .to(SlackHelper::class.java)

    install(DeploymentModule(wisp.deployment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    install(HibernateTestingModule(BackfilaDb::class))
    install(BackfilaPersistenceModule(config))

    install(ServiceWebActionsModule())

    bind(BackfilaCallbackConnectorProvider::class.java)
      .to(FakeBackfilaCallbackConnectorProvider::class.java)

    bind(BackfillRunnerLoggingSetupProvider::class.java)
      .to(BackfillRunnerNoLoggingSetupProvider::class.java)

    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(MiskCaller::class)
      }
    },
    )

    newMapBinder<String, BackfilaCallbackConnectorProvider>(ForConnectors::class)
      .addBinding(Connectors.HTTP)
      .to(FakeBackfilaCallbackConnectorProvider::class.java)
    newMapBinder<String, BackfilaCallbackConnectorProvider>(ForConnectors::class)
      .addBinding(Connectors.ENVOY)
      .to(FakeBackfilaCallbackConnectorProvider::class.java)
  }

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    // TODO better executor for testing
    return MoreExecutors.listeningDecorator(
      Executors.newCachedThreadPool(
        ThreadFactoryBuilder()
          .setNameFormat("backfila-runner-%d")
          .build(),
      ),
    )
  }
}
