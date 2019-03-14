package com.squareup.backfila.actions

import com.squareup.backfila.api.ServiceWebActionsModule
import com.squareup.backfila.dashboard.DashboardModule
import com.squareup.backfila.service.BackfilaConfig
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.BackfilaPersistenceModule
import com.squareup.skim.config.SkimConfig
import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.scope.ActionScopedProviderModule

internal class BackfilaWebActionTestingModule : KAbstractModule() {
  override fun configure() {
    val config = SkimConfig.load<BackfilaConfig>("backfila", Environment.TESTING)
    install(EnvironmentModule(Environment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())

    install(HibernateTestingModule(BackfilaDb::class, disableCrossShardQueryDetector = false))
    install(BackfilaPersistenceModule(config))

    install(DashboardModule())
    install(ServiceWebActionsModule())

    install(object : ActionScopedProviderModule() {
      override fun configureProviders() {
        bindSeedData(MiskCaller::class)
      }
    })
  }
}
