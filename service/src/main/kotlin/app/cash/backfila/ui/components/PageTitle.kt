package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.header
import kotlinx.html.span

fun TagConsumer<*>.PageTitle(title: String, subtitle: String? = null) {
    header {
      div("mx-auto max-w-7xl px-200 sm:px-6 lg:px-8s py-10") {
        val maybeSubtitleSuffix = subtitle?.let {": "} ?: ""
        span("text-3xl font-bold leading-tight tracking-tight text-gray-900") { +"$title$maybeSubtitleSuffix" }
        subtitle?.let { span("text-3xl font-bold leading-tight tracking-tight text-green-600") { +it } }
      }
    }
}
