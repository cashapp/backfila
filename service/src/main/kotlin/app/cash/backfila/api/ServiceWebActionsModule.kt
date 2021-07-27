package app.cash.backfila.api

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class ServiceWebActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigureServiceAction>())
    install(WebActionModule.create<CreateAndStartBackfillAction>())
    install(WebActionModule.create<CheckBackfillStatusAction>())
  }
}
