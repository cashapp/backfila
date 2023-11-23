package app.cash.backfila.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class SeedDbWebActionsModule() : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<SeedDbAction>())
  }
}
