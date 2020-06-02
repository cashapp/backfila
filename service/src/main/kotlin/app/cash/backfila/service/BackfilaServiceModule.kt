package app.cash.backfila.service

import app.cash.backfila.api.ServiceWebActionsModule
import app.cash.backfila.client.ConnectorProvider
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.EnvoyClientServiceClientProvider
import app.cash.backfila.client.HttpClientServiceClientProvider
import app.cash.backfila.dashboard.BackfilaDashboardModule
import app.cash.backfila.dashboard.BackfilaWebActionsModule
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import misk.config.ConfigModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
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
  private val environment: Environment,
  private val config: BackfilaConfig
) : KAbstractModule() {
  override fun configure() {
    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("backfila--owners")))

    install(ConfigModule.create("backfila", config))
    install(EnvironmentModule(environment))
    install(BackfilaPersistenceModule(config))
    install(BackfilaWebActionsModule())
    install(BackfilaDashboardModule(environment))
    install(ServiceWebActionsModule())

    install(SchedulerLifecycleServiceModule())

    newMultibinder<Interceptor>(HttpClientNetworkInterceptor::class)

    if (config.slack != null) {
      install(SlackModule(config.slack))
    }

    // TODO:mikepaw Require that the Admin Console is installed so it isn't forgotten.
    // something along the lines of requireBinding but works for multibindings.
  }

  @Provides @Singleton
  fun connectorProvider(
    httpProvider: HttpClientServiceClientProvider,
      envoyProvider: EnvoyClientServiceClientProvider
  ) = ConnectorProvider(
      Connectors.HTTP to httpProvider,
      Connectors.ENVOY to envoyProvider
  )

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(config.backfill_runner_threads ?: 40,
        ThreadFactoryBuilder()
            .setNameFormat("backfila-runner-%d")
            .build()))
  }
}
