package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import misk.tailwind.components.AlertInfo

fun TagConsumer<*>.UseOldUIAlert() {
  AlertInfo("The new Backfila UI is in Beta. Please report any bugs or missing functionality to #backfila-discuss. The old UI will remain available during the testing period.", "Go to Old UI", "/app/")
}
