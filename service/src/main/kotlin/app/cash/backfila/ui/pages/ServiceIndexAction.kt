package app.cash.backfila.ui.pages

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.ui.PathBuilder
import app.cash.backfila.ui.components.AlertSupport
import app.cash.backfila.ui.components.DashboardLayout
import app.cash.backfila.ui.components.PageTitle
import app.cash.backfila.ui.components.ServiceAutocompleteWrapper
import app.cash.backfila.ui.components.ToggleContainer
import javax.inject.Inject
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.legend
import misk.hotwire.buildHtml
import misk.security.authz.Unauthenticated
import misk.turbo.turbo_frame
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class ServiceIndexAction @Inject constructor(
  private val config: BackfilaConfig,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun get(
    @QueryParam sc: String?,
  ): String {
    return buildHtml {
      DashboardLayout(
        title = "Backfila",
        path = PATH,
      ) {
        PageTitle("Services")
        val pathBuilder = PathBuilder(path = PATH)

        ServiceAutocompleteWrapper(redirectPath = ServiceShowAction.PATH)

        ToggleContainer(
          buttonText = "${pathBuilder.countFilters()} Filters",
          labelBlock = {
            a(classes = "text-gray-500") {
              href = PathBuilder(
                path = pathBuilder.path,
                service = pathBuilder.service,
              ).build()

              attributes["target"] = "_top"

              +"""Clear all"""
            }
          },
        ) {
          div("border-gray-200 py-10") {
            id = "filters"
            div(
              "mx-auto grid max-w-7xl grid-cols-4 gap-x-4 px-4 text-sm sm:px-6 md:gap-x-6 lg:px-8",
            ) {
//          div("grid auto-rows-min grid-cols-1 gap-y-10 md:grid-cols-2 md:gap-x-6") {
              div {
                legend("block font-medium") { +"""Status""" }
                // TODO fill in applicable filters
                // div("space-y-6 pt-6 sm:space-y-4 sm:pt-4") {
                //   listOf(
                //     FlagInfoStatus.ACTIVE,
                //     FlagInfoStatus.ROLLED_OUT,
                //     FlagInfoStatus.NEEDS_CLEANUP,
                //     FlagInfoStatus.NEEDS_ARCHIVE,
                //     FlagInfoStatus.NONE,
                //   ).forEach {
                //     div("flex items-center text-base sm:text-sm") {
                //       a {
                //         val selected = pathBuilder.filterFlagInfoStatus.contains(it)
                //         val toggledList = if (selected) {
                //           pathBuilder.filterFlagInfoStatus.toMutableList().minus(it)
                //         } else {
                //           pathBuilder.filterFlagInfoStatus.toMutableList().plus(it)
                //         }
                //         href = pathBuilder.copy(
                //           filterFlagInfoStatus = toggledList
                //         ).build()
                //
                //         input(classes = "h-4 w-4 flex-shrink-0 rounded border-gray-300 text-green-600 focus:ring-green-500") {
                //           id = "status-${it.ordinal}"
                //           name = "status[]"
                //           value = "${it.ordinal}"
                //           type = InputType.checkBox
                //           checked = selected
                //         }
                //         label("cursor-pointer ml-3 min-w-0 flex-1 text-gray-600") {
                //           htmlFor = "status-0"
                //           FlagStatusBadge(it)
                //         }
                //       }
                //     }
                //   }
                // }
              }
              div {
                legend("block font-medium") { +"""Category""" }
                // div("space-y-6 pt-6 sm:space-y-4 sm:pt-4") {
                //   listOf(
                //     FlagCategory.ROLLOUT,
                //     FlagCategory.EXPERIMENT,
                //     FlagCategory.PERMANENT_CONFIG,
                //     FlagCategory.UNCATEGORIZED,
                //   ).forEach {
                //     div("flex items-center text-base sm:text-sm") {
                //       a {
                //         val selected = pathBuilder.filterFlagCategory.contains(it)
                //         val toggledList = if (selected) {
                //           pathBuilder.filterFlagCategory.toMutableList().minus(it)
                //         } else {
                //           pathBuilder.filterFlagCategory.toMutableList().plus(it)
                //         }
                //         href = pathBuilder.copy(
                //           filterFlagCategory = toggledList
                //         ).build()
                //
                //         input(classes = "h-4 w-4 flex-shrink-0 rounded border-gray-300 text-green-600 focus:ring-green-500 hover:bg-green") {
                //           id = "category-${it.ordinal}"
                //           name = "category[]"
                //           value = "${it.ordinal}"
                //           type = InputType.checkBox
                //           checked = selected
                //         }
                //         label("cursor-pointer ml-3 min-w-0 flex-1 text-gray-600") {
                //           htmlFor = "category-0"
                //           FlagCategoryBadge(it)
                //         }
                //       }
                //     }
                //   }
                // }
              }
              div {
                legend("block font-medium") { +"""Other""" }
                // div("space-y-6 pt-6 sm:space-y-4 sm:pt-4") {
                //
                //   div("flex items-center text-base sm:text-sm") {
                //     a {
                //       val selected = pathBuilder.filterDivergent
                //       href = pathBuilder.copy(
                //         filterDivergent = !pathBuilder.filterDivergent
                //       ).build()
                //
                //       input(classes = "h-4 w-4 flex-shrink-0 rounded border-gray-300 text-green-600 focus:ring-green-500") {
                //         id = "divergent"
                //         name = "divergent"
                //         value = "${selected}"
                //         type = InputType.checkBox
                //         checked = selected
                //       }
                //       label("cursor-pointer ml-3 min-w-0 flex-1 text-gray-600") {
                //         htmlFor = "divergent-0"
                //         +"Divergent"
                //       }
                //     }
                //   }
                //
                //   div("flex items-center text-base sm:text-sm") {
                //     a {
                //       val selected = pathBuilder.filterMyFlags
                //       href = pathBuilder.copy(
                //         filterMyFlags = !pathBuilder.filterMyFlags
                //       ).build()
                //
                //       input(classes = "h-4 w-4 flex-shrink-0 rounded border-gray-300 text-green-600 focus:ring-green-500") {
                //         id = "my-flags"
                //         name = "my-flags"
                //         value = "${selected}"
                //         type = InputType.checkBox
                //         checked = selected
                //       }
                //       label("cursor-pointer ml-3 min-w-0 flex-1 text-gray-600") {
                //         htmlFor = "my-flags-0"
                //         +"My Flags"
                //       }
                //     }
                //   }
                // }
              }
            }
          }
        }

        form {
          action = ""
          input {
            placeholder = "Search yo"
          }
        }

        turbo_frame("") {
        }

        AlertSupport(config.support_button_label, config.support_button_url)
      }
    }
  }

  companion object {
    const val PATH = "/"
  }
}
