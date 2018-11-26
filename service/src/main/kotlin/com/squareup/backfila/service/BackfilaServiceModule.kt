package com.squareup.backfila.service

import com.squareup.backfila.actions.PingAction
import com.squareup.skim.SkimModule
import com.squareup.skim.logging.SkimLogging
import misk.MiskCaller
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.DevelopmentOnly
import misk.web.actions.WebActionEntry
import misk.web.metadata.AdminDashboardAccess

class BackfilaServiceModule : KAbstractModule() {
  override fun configure() {
    SkimLogging.configure()
    val environment = Environment.fromEnvironmentVariable()
    install(BackfilaModule(environment))

    install(SkimModule(environment))
    multibind<AccessAnnotationEntry>().toInstance(
        AccessAnnotationEntry<AdminDashboardAccess>(roles = listOf("cash-eng")))
    bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
        .toInstance(MiskCaller(user = "development", roles = setOf("cash-eng")))

    multibind<WebActionEntry>().toInstance(WebActionEntry<PingAction>())
  }
}