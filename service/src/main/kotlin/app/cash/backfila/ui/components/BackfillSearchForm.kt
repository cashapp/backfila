package app.cash.backfila.ui.components

import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.style
import kotlinx.html.ul

private const val INPUT_CLASSES = "block w-full rounded-md border-gray-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm"
private const val LABEL_CLASSES = "block text-sm font-medium text-gray-700 mb-1"
private const val RESULTS_CLASSES = "absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none sm:text-sm"
private const val BUTTON_BASE_CLASSES = "inline-flex items-center px-4 py-2 border text-sm font-medium rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
private const val PRIMARY_BUTTON_CLASSES = "$BUTTON_BASE_CLASSES border-transparent shadow-sm text-white bg-indigo-600 hover:bg-indigo-700"
private const val SECONDARY_BUTTON_CLASSES = "$BUTTON_BASE_CLASSES border-gray-300 text-gray-700 bg-white hover:bg-gray-50"

fun TagConsumer<*>.BackfillSearchForm(
  backfillName: String? = null,
  createdByUser: String? = null,
  serviceName: String,
  variantName: String,
) {
  div("px-4 sm:px-6 lg:px-8 py-4 bg-gray-50 border-b border-gray-200") {
    form(classes = "flex flex-wrap gap-4 items-end") {
      method = kotlinx.html.FormMethod.get
      attributes["data-turbo-frame"] = "_top"
      action = buildActionUrl(serviceName, variantName)

      // Backfill Name Search
      autocompleteField(
        id = "backfill_name",
        name = "backfill_name",
        label = "Backfill Name",
        placeholder = "Type to search backfills...",
        value = backfillName,
        searchUrl = "/services/$serviceName/variants/$variantName/backfill-names/search",
      )

      // Created By User Search
      autocompleteField(
        id = "created_by_user",
        name = "created_by_user", label = "Created by",
        placeholder = "test.user",
        value = createdByUser,
        searchUrl = "/services/$serviceName/variants/$variantName/users/search",
      )

      div("flex gap-2") {
        button(classes = PRIMARY_BUTTON_CLASSES) {
          type = ButtonType.submit
          +"FILTER"
        }

        a(classes = SECONDARY_BUTTON_CLASSES) {
          href = buildActionUrl(serviceName, variantName)
          +"CLEAR FILTERS"
        }
      }
    }
  }
}

private fun buildActionUrl(serviceName: String, variantName: String): String {
  return "/services/$serviceName" + if (variantName != "default") "/$variantName" else "/"
}

private fun TagConsumer<*>.autocompleteField(
  id: String,
  name: String,
  label: String,
  placeholder: String,
  value: String?,
  searchUrl: String,
) {
  div("flex-1 min-w-0 relative") {
    label(LABEL_CLASSES) {
      htmlFor = id
      +label
    }

    div {
      attributes["data-controller"] = "autocomplete"
      attributes["data-autocomplete-url-value"] = searchUrl
      attributes["data-autocomplete-min-length-value"] = "2"
      attributes["role"] = "combobox"

      input(classes = INPUT_CLASSES) {
        type = InputType.text
        attributes["id"] = id
        this.name = name
        this.placeholder = placeholder
        this.value = value ?: ""
        attributes["data-autocomplete-target"] = "input"
        attributes["autocomplete"] = "off"
      }

      ul(RESULTS_CLASSES) {
        attributes["data-autocomplete-target"] = "results"
        attributes["role"] = "listbox"
        attributes["id"] = "$id-results"
        style = "display: none;"
      }
    }
  }
}
