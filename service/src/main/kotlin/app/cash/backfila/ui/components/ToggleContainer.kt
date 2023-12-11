package app.cash.backfila.ui.components

import kotlinx.html.ButtonType
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.section
import kotlinx.html.span
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon

fun TagConsumer<*>.ToggleContainer(
  buttonText: String,
  borderless: Boolean = false,
  labelIsButton: Boolean = false,
  isOpen: Boolean = false,
  justifyBetween: Boolean = false,
  labelBlock: TagConsumer<*>.() -> Unit = {},
  hiddenBlock: TagConsumer<*>.() -> Unit,
) {
  val borderStyle = if (!borderless) "border-b border-t border-gray-200" else ""
  section("grid items-center $borderStyle") {
    attributes["data-controller"] = "toggle"

    attributes["aria-labelledby"] = "info-heading"
    h2("sr-only") {
      id = "filter-heading"
      +"""Filters"""
    }
    div("relative col-start-1 row-start-1") {
      if (labelIsButton) {
        button(classes = "w-full font-medium text-gray-700 py-4") {
          attributes["data-action"] = "toggle#toggle"

          type = ButtonType.button
          attributes["aria-controls"] = "toggle-container-1"
          attributes["aria-expanded"] = "false"

          rowContents(buttonText, labelIsButton, justifyBetween, labelBlock)
        }
      } else {
        div(classes = "w-full font-medium text-gray-700 py-4") {
          rowContents(buttonText, labelIsButton, justifyBetween, labelBlock)
        }
      }
    }

    val containerBorderStyle = if (!borderless) "border-t border-gray-200" else ""
    val hiddenStyle = if (isOpen) "" else "hidden"
    div("$containerBorderStyle $hiddenStyle px-4 sm:px-6 lg:px-8") {
      attributes["data-toggle-target"] = "toggleable"
      attributes["data-css-class"] = "hidden"

      id = "toggle-container-1"

      hiddenBlock()
    }
  }
}

private fun TagConsumer<*>.rowContents(
  buttonText: String,
  fullWidthButton: Boolean = false,
  justifyBetween: Boolean = false,
  labelBlock: TagConsumer<*>.() -> Unit = {},
) {
  val justifyStyle = if (justifyBetween) "justify-between" else "justify-end"
  div("$justifyStyle mx-auto flex max-w-7xl space-x-6 divide-x divide-gray-200 text-sm text-left px-4 sm:px-6 lg:px-8") {
    labelBlock()

    div(classes = "pl-6") {
      div(classes = "group flex items-center font-medium text-gray-700") {
        if (fullWidthButton) {
          span("pr-4") {
            +buttonText
          }
          toggleIcon()
        } else {
          button(classes = "group flex pr-4 font-medium text-gray-700") {
            attributes["data-action"] = "toggle#toggle"

            type = ButtonType.button
            attributes["aria-controls"] = "toggle-container-1"
            attributes["aria-expanded"] = "false"
            +buttonText

            toggleIcon()
          }
        }
      }
    }
  }
}

private fun TagConsumer<*>.toggleIcon() {
  // Toggle icon on click
  div("") {
    attributes["data-toggle-target"] = "toggleable"
    attributes["data-css-class"] = "hidden"
    heroicon(Heroicons.MINI_CHEVRON_DOWN)
  }
  div("hidden") {
    attributes["data-toggle-target"] = "toggleable"
    attributes["data-css-class"] = "hidden"
    heroicon(Heroicons.MINI_CHEVRON_UP)
  }
}
