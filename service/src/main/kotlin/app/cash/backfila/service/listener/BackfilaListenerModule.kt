package app.cash.backfila.service.listener

import misk.inject.KAbstractModule

class BackfilaListenerModule : KAbstractModule() {
  override fun configure() {
    newMultibinder<BackfillRunListener>()
    multibind<BackfillRunListener>().to<AuditClientListener>()
    multibind<BackfillRunListener>().to<SlackHelper>()
  }
}
