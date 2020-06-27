package app.cash.backfila.service

import app.cash.backfila.api.ServiceWebActionsModule
import app.cash.backfila.client.BackfilaClientServiceClientProvider
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.EnvoyClientServiceClientProvider
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.client.HttpClientServiceClientProvider
import app.cash.backfila.dashboard.BackfilaDashboardModule
import app.cash.backfila.dashboard.BackfilaWebActionsModule
import app.cash.backfila.service.persistence.BackfilaPersistenceModule
import app.cash.backfila.service.scheduler.ForBackfilaScheduler
import app.cash.backfila.service.scheduler.RunnerSchedulerServiceModule
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import misk.config.ConfigModule
import misk.environment.Deployment
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.slack.SlackModule
import misk.web.dashboard.AdminDashboardAccess
import okhttp3.Interceptor
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
annotation class HttpClientNetworkInterceptor

class BackfilaServiceModule(
  private val deployment: Deployment,
  private val config: BackfilaConfig
) : KAbstractModule() {
  override fun configure() {
    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("backfila--owners")))

    install(ConfigModule.create("backfila", config))
    install(BackfilaPersistenceModule(config))
    install(BackfilaWebActionsModule())
    install(BackfilaDashboardModule(deployment))
    install(ServiceWebActionsModule())

    install(RunnerSchedulerServiceModule())

    newMapBinder<String, BackfilaClientServiceClientProvider>(ForConnectors::class)
        .addBinding(Connectors.HTTP)
        .to(HttpClientServiceClientProvider::class.java)
    newMapBinder<String, BackfilaClientServiceClientProvider>(ForConnectors::class)
        .addBinding(Connectors.ENVOY)
        .to(EnvoyClientServiceClientProvider::class.java)

    newMultibinder<Interceptor>(HttpClientNetworkInterceptor::class)

    if (config.slack != null) {
      install(SlackModule(config.slack))
    }

    // TODO:mikepaw Require that the Admin Console is installed so it isn't forgotten.
    // something along the lines of requireBinding but works for multibindings.
  }

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(config.backfill_runner_threads ?: 40,
        ThreadFactoryBuilder()
            .setNameFormat("backfila-runner-%d")
            .build()))
  }
}
