package com.squareup.backfila.dashboard

import com.squareup.protos.backfila.service.ConfigureServiceRequest
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject

class GetDashboardAction @Inject constructor() : WebAction {

  @Get("/dashboard")
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Unauthenticated
  fun loadDashboard(): ConfigureServiceRequest.BackfillData {
    return ConfigureServiceRequest.BackfillData("test", listOf(), "com.squareup.inclass", "com.squareup.outclass", false)
//    return UiDashboard(
//        listOf(
//            UiBackfill(
//                "BackfillRavenTemplates",
//                0.1f,
//                UiBackfill.State.RUNNING
//            ),
//            UiBackfill(
//                "BackfillClientSyncCardTransactions",
//                0.0f,
//                UiBackfill.State.FAILED
//            )
//        )
//    )
  }
}

data class UiDashboard(val backfills: List<UiBackfill>)

data class UiBackfill(
    val name: String,
    val progress: Float,
    val state: State
) {

  enum class State {
    READY, RUNNING, COMPLETE, FAILED
  }
}