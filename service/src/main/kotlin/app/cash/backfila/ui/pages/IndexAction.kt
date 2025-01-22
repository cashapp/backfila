package app.cash.backfila.ui.pages

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.ui.actions.ServiceDataHelper
import app.cash.backfila.ui.components.DashboardPageLayout
import javax.inject.Inject
import kotlinx.html.dd
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.h1
import kotlinx.html.h3
import kotlinx.html.span
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class IndexAction @Inject constructor(
  private val config: BackfilaConfig,
  private val serviceDataHelper: ServiceDataHelper,
  private val dashboardPageLayout: DashboardPageLayout,
  private val callerProvider: ActionScoped<MiskCaller?>,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(
    @QueryParam sc: String?,
  ): String = dashboardPageLayout
    .newBuilder()
    .title("Backfila Home")
    .build {
      h1("text-2xl") {
        +"""Welcome to Backfila, """
        span("font-bold font-mono") { +"""${callerProvider.get()?.user}""" }
        +"""!"""
      }

      // Stats

      div("py-10") {
        h3("text-base font-semibold text-gray-900") { +"""Last 30 days""" }
        dl("mt-5 grid grid-cols-1 gap-5 sm:grid-cols-3") {
          div("overflow-hidden rounded-lg bg-white px-4 py-5 shadow sm:p-6") {
            this@dl.dt("truncate text-sm font-medium text-gray-500") { +"""Total Records""" }
            this@dl.dd("mt-1 text-3xl font-semibold tracking-tight text-gray-900") { +"""1,271,897""" }
          }
          div("overflow-hidden rounded-lg bg-white px-4 py-5 shadow sm:p-6") {
            this@dl.dt("truncate text-sm font-medium text-gray-500") { +"""Avg. Progress Rate""" }
            this@dl.dd("mt-1 text-3xl font-semibold tracking-tight text-gray-900") { +"""58.16%""" }
          }
          div("overflow-hidden rounded-lg bg-white px-4 py-5 shadow sm:p-6") {
            this@dl.dt("truncate text-sm font-medium text-gray-500") { +"""Avg. Records Per Second""" }
            this@dl.dd("mt-1 text-3xl font-semibold tracking-tight text-gray-900") { +"""2457""" }
          }
        }
      }

      // Running Backfills
    }

  companion object {
    const val PATH = "/"
  }
}
