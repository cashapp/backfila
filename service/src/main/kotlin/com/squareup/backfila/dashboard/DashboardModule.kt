package com.squareup.backfila.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class DashboardModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<GetDashboardAction>())
  }
}