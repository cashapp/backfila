package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer

fun TagConsumer<*>.UseOldUIAlert() {
  AlertError("The new Backfila UI is not ready yet. It has bugs and missing functionality. Please use the old UI for now.", "Go to Old UI", "/app/")
}
