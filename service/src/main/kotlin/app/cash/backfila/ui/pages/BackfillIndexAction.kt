package app.cash.backfila.ui.pages

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.ServiceQuery
import app.cash.backfila.ui.components.DashboardPageLayout
import app.cash.backfila.ui.components.PageTitle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class BackfillIndexAction @Inject constructor(
  private val dashboardPageLayout: DashboardPageLayout,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun get(): Response<ResponseBody> {
    val backfills = transacter.transaction { session ->
      queryFactory.newQuery<BackfillRunQuery>()
        .orderByUpdatedAtDesc()
        .apply {
          maxRows = 10
        }
        .list(session)
    }

    return Response(
      dashboardPageLayout.newBuilder()
        .title("Backfills | Backfila")
        .buildHtmlResponseBody {
          PageTitle("Backfills") {
            a {
              href = BackfillCreateIndexAction.PATH

              button(classes = "rounded-full bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600") {
                type = ButtonType.button
                +"""Create"""
              }
            }
          }

          // List of Services
          div("py-10") {
            ul("grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3") {
              role = "list"

              backfills.map { backfill ->
                a {
                  href = BackfillShowAction.path(backfill.id.toString())

                  this@ul.li("registration col-span-1 divide-y divide-gray-200 rounded-lg bg-white shadow") {
                    div("flex w-full items-center justify-between space-x-6 p-6") {
                      div("flex-1 truncate") {
                        div("flex items-center space-x-3") {
                          // TODO do proper join query
                          val service = transacter.transaction { session ->
                            queryFactory.newQuery<ServiceQuery>()
                              .id(backfill.service_id)
                              .uniqueResult(session)!!
                          }

                          // Don't include default variant in label, only for unique variants
                          val label =
                            if (service.variant == "default") service.registry_name else service.registry_name + "/" + service.variant
                          h3("truncate text-sm font-medium text-gray-900") {
                            +"""$label #${backfill.id}"""
                          }
                          span("inline-flex shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20") {
                            // TODO add color per state
                            +backfill.state.name
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        },
    )
  }

  companion object {
    const val PATH = "/backfills/"
  }
}
