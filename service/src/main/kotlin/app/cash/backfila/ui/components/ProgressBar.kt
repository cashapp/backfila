package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.style
import kotlin.math.round

fun TagConsumer<*>.ProgressBar(numerator: Number, denominator: Number) {
  div("w-full bg-gray-200 rounded-full dark:bg-gray-700") {
    val percentComplete = if (numerator.toInt() == 0) 0 else round((numerator.toDouble() / denominator.toDouble()) * 100)
    // Don't show blue sliver for 0%, just show the gray empty bar
    val showPartialBarStyle = if (percentComplete.toInt() != 0) "bg-blue-600" else ""
    div(
      "$showPartialBarStyle text-xs font-medium text-blue-100 text-center p-0.5 leading-none rounded-full"
    ) {
      style = "width: ${if (percentComplete.toInt() == 0) 100 else percentComplete}%"
      +"""$percentComplete%"""
    }
  }
}