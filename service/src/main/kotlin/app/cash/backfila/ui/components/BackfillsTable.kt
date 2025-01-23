package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.UiBackfillRun
import app.cash.backfila.ui.pages.BackfillShowAction
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.table
import kotlinx.html.tbody
import kotlinx.html.td
import kotlinx.html.th
import kotlinx.html.thead
import kotlinx.html.tr

fun TagConsumer<*>.BackfillsTable(running: Boolean, backfills: List<UiBackfillRun>) {
  val title = if (running) "Running" else "Paused"

  div("px-4 sm:px-6 lg:px-8 py-5") {
    div("sm:flex sm:items-center") {
      div("sm:flex-auto") {
        h1("text-base font-semibold leading-6 text-gray-900") { +"""Backfills ($title)""" }
      }
    }
    div("mt-8 flow-root") {
      div("-mx-4 -my-2 overflow-x-auto sm:-mx-6 lg:-mx-8") {
        div("inline-block min-w-full py-2 align-middle sm:px-6 lg:px-8") {
          table("min-w-full divide-y divide-gray-300") {
            thead {
              tr {
                listOf("ID", "Name", "State", "Dry Run", "Progress", "Created by", "Created at", "Last active at").map {
                  th(
                    classes = "py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0",
                  ) { +it }
                }
              }
            }
            tbody("divide-y divide-gray-200") {
              backfills.forEach {
                tr {
                  td(
                    "whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0",
                  ) { +it.id }
                  td(
                    "whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0",
                  ) {
                    a(classes = "text-green-500 hover:underline") {
                      href = BackfillShowAction.PATH.replace("{id}", it.id)
                      +it.name
                    }
                  }

                  listOf(it.state, it.dry_run).map {
                    td(
                      "whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0",
                    ) { +"""$it""" }
                  }
                  td(
                    "whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0",
                  ) {
                    ProgressBar(it.backfilled_matching_record_count, it.computed_matching_record_count)
                  }
                  listOf(it.created_by_user, it.created_at, it.last_active_at).map {
                    td(
                      "whitespace-nowrap py-4 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0",
                    ) { +"""$it""" }
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
