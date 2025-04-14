package app.cash.backfila.ui

import app.cash.backfila.ui.actions.BackfillCreateHandlerAction
import app.cash.backfila.ui.actions.BackfillShowButtonHandlerAction
import app.cash.backfila.ui.pages.BackfillCreateAction
import app.cash.backfila.ui.pages.BackfillCreateIndexAction
import app.cash.backfila.ui.pages.BackfillCreateServiceIndexAction
import app.cash.backfila.ui.pages.BackfillIndexAction
import app.cash.backfila.ui.pages.BackfillShowAction
import app.cash.backfila.ui.pages.IndexAction
import app.cash.backfila.ui.pages.ServiceIndexAction
import app.cash.backfila.ui.pages.ServiceShowAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class UiModule : KAbstractModule() {
  override fun configure() {
    // Pages
    install(WebActionModule.create<IndexAction>())
    install(WebActionModule.create<ServiceIndexAction>())
    install(WebActionModule.create<ServiceShowAction>())
    install(WebActionModule.create<BackfillCreateIndexAction>())
    install(WebActionModule.create<BackfillCreateServiceIndexAction>())
    install(WebActionModule.create<BackfillCreateAction>())
    install(WebActionModule.create<BackfillIndexAction>())
    install(WebActionModule.create<BackfillShowAction>())

    // Other
    install(WebActionModule.create<BackfillCreateHandlerAction>())
    install(WebActionModule.create<BackfillShowButtonHandlerAction>())
  }
}

/**
 * Identifies the Misk dashboard for Backfila so dashboard links and other customization can be added.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class BackfilaDashboard
