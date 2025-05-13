package app.cash.backfila.service

import app.cash.backfila.api.ServiceWebActionsModule
import app.cash.backfila.client.BackfilaCallbackConnectorProvider
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.EnvoyCallbackConnectorProvider
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.client.GrpcCallbackConnectorProvider
import app.cash.backfila.client.HttpCallbackConnectorProvider
import app.cash.backfila.dashboard.BackfilaDashboardModule
import app.cash.backfila.dashboard.BackfilaWebActionsModule
import app.cash.backfila.service.listener.BackfilaListenerModule
import app.cash.backfila.service.persistence.BackfilaPersistenceModule
import app.cash.backfila.service.runner.BackfillRunnerLoggingSetupProvider
import app.cash.backfila.service.runner.BackfillRunnerNoLoggingSetupProvider
import app.cash.backfila.service.scheduler.ForBackfilaScheduler
import app.cash.backfila.service.scheduler.RunnerSchedulerServiceModule
import app.cash.backfila.service.deletion.DeleteByNotificationModule
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import misk.config.ConfigModule
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.slack.SlackModule
import misk.web.dashboard.AdminDashboardAccess
import okhttp3.Interceptor
import wisp.deployment.Deployment

@Qualifier
annotation class HttpClientNetworkInterceptor

class BackfilaServiceModule(
  private val deployment: Deployment,
  private val config: BackfilaConfig,
  private val runnerLoggingSetupProvider: Class<out BackfillRunnerLoggingSetupProvider> =
    BackfillRunnerNoLoggingSetupProvider::class.java,
) : KAbstractModule() {
  override fun configure() {
    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("backfila--owners")),
    )

    install(BackfilaListenerModule())

    install(ConfigModule.create("backfila", config))
    install(BackfilaPersistenceModule(config))
    install(BackfilaWebActionsModule())
    install(BackfilaDashboardModule(deployment))
    install(ServiceWebActionsModule())

    install(RunnerSchedulerServiceModule())
    install(DeleteByNotificationModule())

    newMapBinder<String, BackfilaCallbackConnectorProvider>(ForConnectors::class)
      .addBinding(Connectors.HTTP)
      .to(HttpCallbackConnectorProvider::class.java)
    newMapBinder<String, BackfilaCallbackConnectorProvider>(ForConnectors::class)
      .addBinding(Connectors.ENVOY)
      .to(EnvoyCallbackConnectorProvider::class.java)
    newMapBinder<String, BackfilaCallbackConnectorProvider>(ForConnectors::class)
      .addBinding(Connectors.GRPC)
      .to(GrpcCallbackConnectorProvider::class.java)

    newMultibinder<Interceptor>(HttpClientNetworkInterceptor::class)

    bind<BackfillRunnerLoggingSetupProvider>().to(runnerLoggingSetupProvider)

    if (config.slack != null) {
      install(SlackModule(config.slack))
    }

    // TODO:mikepaw Require that the Admin Console is installed so it isn't forgotten.
    // something along the lines of requireBinding but works for multibindings.
  }

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    return MoreExecutors.listeningDecorator(
      Executors.newFixedThreadPool(
        config.backfill_runner_threads ?: 40,
        ThreadFactoryBuilder()
          .setNameFormat("backfila-runner-%d")
          .build(),
      ),
    )
  }
}
