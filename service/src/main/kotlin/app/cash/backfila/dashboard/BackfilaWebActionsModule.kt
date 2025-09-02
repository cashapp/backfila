package app.cash.backfila.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule

class BackfilaWebActionsModule() : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<StopAllBackfillsAction>())
    install(WebActionModule.create<GetServicesAction>())
    install(WebActionModule.create<GetServiceVariantsAction>())
    install(WebActionModule.create<CloneBackfillAction>())
    install(WebActionModule.create<CreateBackfillAction>())
    install(WebActionModule.create<StartBackfillAction>())
    install(WebActionModule.create<StopBackfillAction>())
    install(WebActionModule.create<GetRegisteredBackfillsAction>())
    install(WebActionModule.create<GetBackfillRunsAction>())
    install(WebActionModule.create<GetServiceDetailsAction>())
    install(WebActionModule.create<GetBackfillStatusAction>())
    install(WebActionModule.create<UpdateBackfillAction>())
    install(WebActionModule.create<ViewLogsAction>())
    install(WebActionModule.create<EditPartitionCursorAction>())
    install(WebActionModule.create<EditPartitionCursorHandlerAction>())
    install(WebActionModule.create<SearchBackfillNamesAction>())
    install(WebActionModule.create<SearchUsersAction>())
  }
}
