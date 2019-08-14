package com.squareup.backfila.dashboard

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.DashboardTab
import misk.web.WebActionModule
import misk.web.actions.AdminDashboardTab
import misk.web.metadata.WebTabResourceModule

class DashboardWebActionsModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<GetServicesAction>())
    install(WebActionModule.create<CreateBackfillAction>())
    install(WebActionModule.create<StartBackfillAction>())
    install(WebActionModule.create<StopBackfillAction>())
    install(WebActionModule.create<GetRegisteredBackfillsAction>())
    install(WebActionModule.create<GetBackfillRunsAction>())
    install(WebActionModule.create<GetBackfillStatusAction>())

    // Tabs
    multibind<DashboardTab, AdminDashboardTab>().toInstance(DashboardTab(
        name = "Home",
        slug = "home",
        url_path_prefix = "/app/home/",
        category = "Backfila"
    ))
    install(WebTabResourceModule(
        environment = environment,
        slug = "home",
        web_proxy_url = "http://localhost:4200/",
        url_path_prefix = "/app/home/",
        resourcePath = "classpath:/web/app/home/"
    ))
  }
}