package com.squareup.backfila.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class DashboardWebActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<GetServicesAction>())
    install(WebActionModule.create<CreateBackfillAction>())
    install(WebActionModule.create<StartBackfillAction>())
    install(WebActionModule.create<StopBackfillAction>())
    install(WebActionModule.create<GetRegisteredBackfillsAction>())
    install(WebActionModule.create<GetBackfillRunsAction>())
    install(WebActionModule.create<GetBackfillStatusAction>())
  }
}