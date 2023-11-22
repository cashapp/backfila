package com.squareup.cash.monitorcheckup.ui

import app.cash.backfila.ui.actions.ServiceAutocompleteAction
import app.cash.backfila.ui.pages.IndexAction
import app.cash.backfila.ui.pages.ListMonitorsAction
import app.cash.backfila.ui.pages.ServiceStatusAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class UiModule : KAbstractModule() {
  override fun configure() {
    // Pages
    install(WebActionModule.create<ServiceStatusAction>())
    install(WebActionModule.create<IndexAction>())
    install(WebActionModule.create<ListMonitorsAction>())

    // Other
    install(WebActionModule.create<ServiceAutocompleteAction>())
  }
}
