package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.div

// TODO
//   Currently this reloads the whole page every 10s
//   We want to ideally only update within a turbo frame so the rest of the UI is stable
//     (ie. update form for backfill show page)
fun TagConsumer<*>.AutoReload(block: TagConsumer<*>.() -> Unit) {
  div {
    attributes["data-controller"] = "auto-reload"
    attributes["data-auto-reload-target"] = "frame"

    block()
  }
}
