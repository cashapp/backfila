package com.squareup.backfila.service

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
    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(roles = listOf("eng")))
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
        .toInstance(MiskCaller(user = "development", roles = setOf("eng")))

    bind(BackfilaClientServiceClientProvider::class.java)
        .to(RealBackfilaClientServiceClientProvider::class.java)
  }
}
