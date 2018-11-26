package com.squareup.backfila.dashboard

import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry

class DashboardModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry<GetDashboardAction>())
  }
}