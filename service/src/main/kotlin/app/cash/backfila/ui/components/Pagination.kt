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
    // Show Previous button if we're not on the first page
    if (offset?.isNotBlank() == true) {
      div("-mt-px flex w-0 flex-1") {
        a(classes = "inline-flex items-center border-t-2 border-transparent pt-4 pr-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700") {
          href = if (lastOffset?.isNotBlank() == true) {
            // We have a proper lastOffset, use it
            basePath.appendOffsets(lastOffset)
          } else {
            // Fallback: go to first page
            basePath
          }
          heroicon(Heroicons.MINI_ARROW_LONG_LEFT)
          +"""Previous"""
        }
      }
    }

    if (nextOffset?.isNotBlank() == true) {
      div("-mt-px flex w-0 flex-1 justify-end") {
        a(classes = "inline-flex items-center border-t-2 border-transparent pt-4 pl-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700") {
          href = basePath.appendOffsets(nextOffset, offset)
          +"""Next"""
          heroicon(Heroicons.MINI_ARROW_LONG_RIGHT)
        }
      }
    }
  }
}

fun TagConsumer<*>.PaginationWithHistory(
  nextOffset: String?,
  offset: String?,
  historyChain: String?,
  basePath: String,
) {
  nav("mt-12 flex items-center justify-between border-t border-gray-200 px-4 sm:px-0") {
    // Show Previous button if we have history or are not on first page
    val hasHistory = historyChain?.isNotBlank() == true
    val notOnFirstPage = offset?.isNotBlank() == true

    if (hasHistory || notOnFirstPage) {
      div("-mt-px flex w-0 flex-1") {
        a(classes = "inline-flex items-center border-t-2 border-transparent pt-4 pr-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700") {
          href = if (hasHistory) {
            val historyList = historyChain!!.split(",")
            val previousOffset = historyList.last()
            val remainingHistory = if (historyList.size > 1) {
              historyList.dropLast(1).joinToString(",")
            } else {
              null
            }

            if (remainingHistory?.isNotBlank() == true) {
              "$basePath?offset=$previousOffset&history=$remainingHistory"
            } else {
              basePath.appendOffsets(previousOffset)
            }
          } else {
            // Fallback: go to first page
            basePath
          }
          heroicon(Heroicons.MINI_ARROW_LONG_LEFT)
          +"""Previous"""
        }
      }
    }

    if (nextOffset?.isNotBlank() == true) {
      div("-mt-px flex w-0 flex-1 justify-end") {
        a(classes = "inline-flex items-center border-t-2 border-transparent pt-4 pl-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700") {
          href = basePath.appendOffsetsWithHistory(nextOffset, offset, historyChain)
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

  if (lastOffset?.isNotBlank() == true) {
    if (pathBuilder.contains("?")) {
      pathBuilder.append("&")
    } else {
      pathBuilder.append("?")
    }
    pathBuilder.append("lastOffset=").append(lastOffset)
  }
  return pathBuilder.toString()
}

fun String.appendOffsetsWithHistory(nextOffset: String? = null, currentOffset: String? = null, historyChain: String? = null): String {
  val pathBuilder = StringBuilder(this)

  if (nextOffset?.isNotBlank() == true) {
    pathBuilder.append("?offset=").append(nextOffset)

    // Build history chain: add current offset to existing history
    val newHistory = if (currentOffset?.isNotBlank() == true) {
      if (historyChain?.isNotBlank() == true) {
        "$historyChain,$currentOffset"
      } else {
        currentOffset
      }
    } else {
      historyChain
    }

    if (newHistory?.isNotBlank() == true) {
      pathBuilder.append("&history=").append(newHistory)
    }
  }
  val result = pathBuilder.toString()
  return result
}
