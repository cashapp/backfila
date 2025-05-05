package app.cash.backfila.dashboard

import app.cash.backfila.ui.UiModule
import misk.inject.KAbstractModule

class BackfilaDashboardModule : KAbstractModule() {
  override fun configure() {
    install(UiModule())
  }
}
