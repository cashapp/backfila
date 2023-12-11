package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer

/** Shows blue Alert banner for support if button label and URL are defined. */
fun TagConsumer<*>.AlertSupport(label: String?, url: String?) {
  if (!label.isNullOrBlank() && !url.isNullOrBlank()) {
    AlertInfoHighlight(
      message = "Questions? Concerns? Need help?",
      label = label,
      link = url,
      spaceAbove = true,
    )
  }
}
