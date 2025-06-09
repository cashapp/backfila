package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div
import misk.turbo.turbo_frame

fun TagConsumer<*>.AutoReload(frameId: String = "auto-reload-frame", block: TagConsumer<*>.() -> Unit) {
  div {
    attributes["data-controller"] = "auto-reload"

    turbo_frame(frameId) {
      attributes["data-auto-reload-target"] = "frame"
      attributes["data-turbo-action"] = "replace"

      block()
    }
  }
}
