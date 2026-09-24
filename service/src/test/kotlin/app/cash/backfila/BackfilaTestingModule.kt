package app.cash.backfila

import app.cash.backfila.api.ServiceWebActionsModule
import app.cash.backfila.client.BackfilaCallbackConnectorProvider
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.FakeBackfilaCallbackConnectorProvider
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.listener.BackfilaListenerModule
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfilaPersistenceModule
import app.cash.backfila.service.runner.BackfillRunnerLoggingSetupProvider
import app.cash.backfila.service.runner.BackfillRunnerNoLoggingSetupProvider
import app.cash.backfila.service.scheduler.ForBackfilaScheduler
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import jakarta.inject.Singleton
import java.util.concurrent.Executors
import misk.MiskCaller
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
import misk.logging.LogCollectorModule
import misk.scope.ActionScopedProviderModule
import misk.slack.webapi.SlackClient
import misk.slack.webapi.helpers.GetUserResponse
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.PostMessageResponse

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
            ),
            reader = null,
          ),
        ),
      ),
      web_url_root = "",
      slack = null,
    )
    bind<BackfilaConfig>().toInstance(config)

    install(BackfilaListenerModule())
    bind<SlackClient>().to<FakeSlackClient>()
    install(AppNameModule("backfila"))
    install(FakeAuditClientModule())

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

@jakarta.inject.Singleton
internal class FakeSlackClient @jakarta.inject.Inject constructor() : SlackClient {
  val postMessageRequests = mutableListOf<PostMessageRequest>()
  var postMessageResponse = PostMessageResponse(ok = true)

  override fun postMessage(request: PostMessageRequest): PostMessageResponse {
    postMessageRequests += request
    return postMessageResponse
  }

  override fun postConfirmation(url: String, request: PostMessageRequest): PostMessageResponse =
    error("Not implemented by fake")

  override fun getUserByEmail(email: String): GetUserResponse = error("Not implemented by fake")

  override fun getUserById(userId: String): GetUserResponse = error("Not implemented by fake")
}
