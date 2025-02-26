package app.cash.backfila.ui.components

import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.nav
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon

fun TagConsumer<*>.Pagination(
  nextOffset: String?,
  offset: String?,
  lastOffset: String?,
  basePath: String,
) {
  nav("mt-12 flex items-center justify-between border-t border-gray-200 px-4 sm:px-0") {
    lastOffset?.let {
      div("-mt-px flex w-0 flex-1") {
        a(classes = "inline-flex items-center border-t-2 border-transparent pt-4 pr-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700") {
          href = basePath.appendOffsets(lastOffset)
          heroicon(Heroicons.MINI_ARROW_LONG_LEFT)
          +"""Previous"""
        }
      }
    }

    if (nextOffset != null) {
      div("-mt-px flex w-0 flex-1 justify-end") {
        a(classes = "inline-flex items-center border-t-2 border-transparent pt-4 pl-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700") {
          href = basePath.appendOffsets(nextOffset, offset ?: "")
          +"""Next"""
          heroicon(Heroicons.MINI_ARROW_LONG_RIGHT)
        }
      }
    }
  }
}

fun String.appendOffsets(nextOffset: String? = null, lastOffset: String? = null): String {
  val pathBuilder = StringBuilder(this)
  if (nextOffset?.isNotBlank() == true) {
    pathBuilder.append("?offset=").append(nextOffset)
  }

  if (lastOffset != null) {
    if (pathBuilder.contains("?")) {
      pathBuilder.append("&")
    } else {
      pathBuilder.append("?")
    }
    pathBuilder.append("lastOffset=").append(lastOffset)
  }
  return pathBuilder.toString()
}
