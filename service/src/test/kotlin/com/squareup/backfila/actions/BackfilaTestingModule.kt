package com.squareup.backfila.actions

import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Provides
import com.squareup.backfila.api.ServiceWebActionsModule
import com.squareup.backfila.client.BackfilaClientServiceClientProvider
import com.squareup.backfila.client.FakeBackfilaClientServiceClientProvider
import com.squareup.backfila.dashboard.DashboardWebActionsModule
import com.squareup.backfila.service.BackfilaConfig
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.BackfilaPersistenceModule
import com.squareup.backfila.service.ForBackfilaScheduler
import com.squareup.skim.config.SkimConfig
import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.scope.ActionScopedProviderModule
import java.util.concurrent.Executors
import javax.inject.Singleton

internal class BackfilaTestingModule : KAbstractModule() {
  override fun configure() {
    val config = SkimConfig.load<BackfilaConfig>("backfila", Environment.TESTING)
    install(EnvironmentModule(Environment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    install(HibernateTestingModule(BackfilaDb::class, disableCrossShardQueryDetector = false))
    install(BackfilaPersistenceModule(config))

    install(DashboardWebActionsModule())
    install(ServiceWebActionsModule())

    bind(BackfilaClientServiceClientProvider::class.java)
        .to(FakeBackfilaClientServiceClientProvider::class.java)

    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(MiskCaller::class)
      }
    })
  }

  @Provides @ForBackfilaScheduler @Singleton
  fun backfillRunnerExecutor(): ListeningExecutorService {
    // TODO better executor for testing
    return MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(ThreadFactoryBuilder()
        .setNameFormat("backfila-runner-%d")
        .build()))
  }
}
