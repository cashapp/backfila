package com.squareup.backfila.service

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import com.squareup.backfila.api.ServiceWebActionsModule
import com.squareup.backfila.client.BackfilaClientServiceClientProvider
import com.squareup.backfila.client.RealBackfilaClientServiceClientProvider
import com.squareup.backfila.dashboard.DashboardWebActionsModule
import com.squareup.skim.SkimModule
import misk.MiskCaller
import misk.config.ConfigModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.DevelopmentOnly
import misk.web.metadata.AdminDashboardAccess
import misk.web.metadata.AdminDashboardTestingModule
import java.util.concurrent.Executors
import javax.inject.Singleton

class BackfilaServiceModule(
  private val environment: Environment,
  private val config: BackfilaConfig
) : KAbstractModule() {
  override fun configure() {
    install(ConfigModule.create("backfila", config))
    install(EnvironmentModule(environment))
    install(SkimModule(environment, config.skim))
    install(BackfilaPersistenceModule(config))
    install(DashboardWebActionsModule())
    install(ServiceWebActionsModule())

    if (environment == Environment.DEVELOPMENT) {
      install(AdminDashboardTestingModule(environment))
    }

    install(SchedulerLifecycleServiceModule())

    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(capabilities = listOf("eng")))
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
        .toInstance(MiskCaller(user = "development", capabilities = setOf("eng")))

    bind(BackfilaClientServiceClientProvider::class.java)
        .to(RealBackfilaClientServiceClientProvider::class.java)
  }

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    return MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(ThreadFactoryBuilder()
        .setNameFormat("backfila-runner-%d")
        .build()))
  }
}
