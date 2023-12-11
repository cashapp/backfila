package app.cash.backfila.ui.components

import app.cash.backfila.ui.pages.ServiceIndexAction
import app.cash.backfila.ui.pages.ServiceShowAction
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.img
import kotlinx.html.nav

fun TagConsumer<*>.NavBar(currentPath: String) {
  val links = listOf(
    Pair(ServiceIndexAction.PATH, "Overview"),
    Pair(ServiceShowAction.PATH.split("{").first(), "Service"),
  )

  nav("bg-white shadow-sm") {
    div("mx-auto max-w-7xl px-4 sm:px-6 lg:px-8") {
      div("flex h-16 justify-between") {
        div("flex") {
          div("flex flex-shrink-0 items-center") {
            a("/") {
              img(classes = "block h-8 w-auto") {
                src = "/static/cash-app.png"
                alt = "Cash App"
              }
            }
          }
          div("sm:-my-px sm:ml-6 sm:flex sm:space-x-8") {
            a(classes = "text-gray-900 inline-flex items-center text-sm font-bold") {
              href = "/"
              +"""Backfila"""
            }
          }
          div("sm:-my-px sm:ml-6 sm:flex sm:space-x-8") {
            val bestMatch = links.map { it.first to Pair(currentPath.startsWith(it.first), it.first.length) }.filter { it.second.first }.sortedByDescending { it.second.second }.first()
            links.map { (path, label) ->
              val selectedStyle = if (path == bestMatch.first) "border-green-500" else ""
              a(classes = "$selectedStyle text-gray-900 inline-flex items-center border-b-2 pt-1 text-sm font-medium") {
                href = path
                if (currentPath.startsWith(path)) {
                  attributes["aria-current"] = "page"
                }
                +label
              }
            }
          }
        }
      }
    }
  }
}
