package app.cash.backfila.ui.components

import com.squareup.cash.monitorcheckup.ui.PathBuilder
import app.cash.backfila.ui.actions.ServiceAutocompleteAction
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.ul
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon

/**
 * Autocomplete input bar for company search
 * Source: https://github.com/afcapel/stimulus-autocomplete
 */
fun TagConsumer<*>.ServiceAutocomplete(
  pagePathBuilder: PathBuilder,
  query: String?,
  inputPlaceholder: String = "Type a service name, use arrow keys to select, then enter to search...",
) {
  div {
    attributes["data-controller"] = "autocomplete"
    attributes["data-autocomplete-url-value"] = ServiceAutocompleteAction.PATH
    attributes["data-autocomplete-submit-on-enter"] = "true"
    attributes["data-autocomplete-selected-class"] = "bg-green-600 text-white"
    attributes["combobox"]

    form("relative mt-1 rounded-md shadow-sm pt") {
      action = pagePathBuilder.build()
      // Updates browser URL for permalinks
      attributes["data-turbo-action"] = "replace"

      div("relative mt-1 rounded-md shadow-sm") {
        // TODO fix CSS issue in production
        div("pointer-events-none absolute inset-y-0 left-0 mt-2.5 items-center pl-3") {
          heroicon(Heroicons.MAGNIFYING_GLASS)
        }
        input(classes = "block w-full rounded-md border-gray-300 pl-10 focus:border-green-500 focus:ring-green-500 sm:text-sm") {
          attributes["data-autocomplete-target"] = "input"

          type = InputType.text
          name = PathBuilder.SearchParam
          id = PathBuilder.SearchParam
          placeholder = inputPlaceholder
          query?.let { value = it }
        }
        input {
          attributes["data-autocomplete-target"] = "hidden"

          type = InputType.hidden
          name = PathBuilder.ServiceParam
          id = PathBuilder.ServiceParam
        }
        ul("list-group absolute z-10 mt-2 rounded-md bg-white shadow-md ring-1 ring-black ring-opacity-5 focus:outline-none text-left") {
          attributes["data-autocomplete-target"] = "results"
        }
      }
    }
  }
}
