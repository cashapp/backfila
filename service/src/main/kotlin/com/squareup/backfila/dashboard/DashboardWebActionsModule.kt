package com.squareup.backfila.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class DashboardWebActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<GetServicesAction>())
    install(WebActionModule.create<CreateBackfillRunAction>())
  }
}