package app.cash.backfila.ui.components

import kotlin.math.round
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.style

fun TagConsumer<*>.ProgressBar(
  numerator: Number,
  denominator: Number,
  precomputingDone: Boolean = true,
) {
  div("w-full bg-gray-200 rounded-full dark:bg-gray-700") {
    val percentComplete = when {
      !precomputingDone -> null // Show indeterminate during precomputing
      denominator.toDouble() <= 0 -> 0.0 // Handle zero denominator
      else -> round((numerator.toDouble() / denominator.toDouble()) * 100)
    }

    // Show indeterminate progress bar during precomputing
    if (percentComplete == null) {
      div("animate-pulse bg-gray-300 text-xs font-medium text-center p-0.5 leading-none rounded-full") {
        style = "width: 100%"
        +"..."
      }
    } else {
      // Don't show blue sliver for 0%, just show the gray empty bar
      val showPartialBarStyle = if (percentComplete > 0) "bg-blue-600" else ""
      div("$showPartialBarStyle text-xs font-medium text-blue-100 text-center p-0.5 leading-none rounded-full") {
        style = "width: $percentComplete%"
        +"${percentComplete.toInt()}%"
      }
    }
  }
}
