package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.span

fun TagConsumer<*>.PageTitle(title: String, service: String?) {
  if (service.isNullOrEmpty() || service == "all") {
    header {
      div("mx-auto max-w-7xl px-200 sm:px-6 lg:px-8s py-10") {
        span("text-3xl font-bold leading-tight tracking-tight text-gray-900") { +"$title: " }
        span("text-3xl font-bold leading-tight tracking-tight text-green-600") { +"All Services" }
      }
    }
  } else {
    header {
      div("mx-auto max-w-7xl py-10") {
        span("text-3xl font-bold leading-tight tracking-tight text-gray-900") { +"$title: " }
        span("text-3xl font-bold leading-tight tracking-tight text-green-600") { +"$service" }
      }
    }
  }
}
