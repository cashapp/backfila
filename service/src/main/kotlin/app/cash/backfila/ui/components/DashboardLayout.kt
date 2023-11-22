package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.main
import kotlinx.html.script
import misk.web.dashboard.HtmlLayout

fun TagConsumer<*>.DashboardLayout(
  title: String,
  path: String,
  block: TagConsumer<*>.() -> Unit = {}
) {
  HtmlLayout(
    appRoot = "/",
    title = title,
    // TODO only use play CDN in development, using it always for demo purporses to avoid UI bugs
//    playCdn = deployment.isLocalDevelopment,
    playCdn = true,
    headBlock = {
      script {
        type = "module"
        src = "/static/js/autocomplete_controller.js"
      }
    },
  ) {
    div("min-h-full") {
      NavBar(path)
      div("py-10") {
        main {
          div("mx-auto max-w-7xl sm:px-6 lg:px-8") {
            block()
          }
        }
      }
    }
  }
}
