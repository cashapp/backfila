package com.squareup.backfila.service

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import com.squareup.backfila.api.ServiceWebActionsModule
import com.squareup.backfila.client.BackfilaClientServiceClientProvider
import com.squareup.backfila.client.Connectors
import com.squareup.backfila.client.EnvoyClientServiceClientProvider
import com.squareup.backfila.client.ForConnectors
import com.squareup.backfila.dashboard.DashboardWebActionsModule
import com.squareup.skim.SkimModule
import com.squareup.skim.aws.Region
import misk.MiskCaller
import misk.config.ConfigModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.DevelopmentOnly
import misk.web.metadata.AdminDashboardAccess
import java.util.concurrent.Executors
import javax.inject.Singleton

class BackfilaServiceModule(
  private val environment: Environment,
  private val config: BackfilaConfig,
  private val region: Region
) : KAbstractModule() {
  override fun configure() {
    install(ConfigModule.create("backfila", config))
    install(EnvironmentModule(environment))
    install(SkimModule(environment, config.skim, region))
    install(BackfilaPersistenceModule(config))
    install(DashboardWebActionsModule(environment))
    install(ServiceWebActionsModule())

    install(SchedulerLifecycleServiceModule())

    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("eng")))
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
        .toInstance(MiskCaller(user = "development", capabilities = setOf("eng")))

    newMapBinder<String, BackfilaClientServiceClientProvider>(ForConnectors::class)
        .addBinding(Connectors.ENVOY)
        .to(EnvoyClientServiceClientProvider::class.java)
    // TODO http connector
  }

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    return MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(40,
        ThreadFactoryBuilder()
            .setNameFormat("backfila-runner-%d")
            .build()))
  }
}
