package app.cash.backfila.ui.components

import app.cash.backfila.ui.PathBuilder
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.label

fun TagConsumer<*>.ServiceAutocompleteWrapper(serviceQuery: String?, redirectPath: String) {
  div("rounded-lg bg-gray-100 my-5") {
    div("px-4 py-5 sm:p-6") {
      div {
        label("block text-sm font-medium leading-6 text-gray-900") {
          htmlFor = "location"
          +"""Service Name"""
        }
        ServiceAutocomplete(
          pagePathBuilder = PathBuilder(
            path = redirectPath,
            query = serviceQuery?.lowercase(),
          )
        )
      }
    }
  }
}
