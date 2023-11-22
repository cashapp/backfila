package app.cash.backfila.ui.actions

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.html.li
import kotlinx.html.role
import misk.hotwire.buildHtml
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

@Singleton
class ServiceAutocompleteAction @Inject constructor() : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun get(
    @QueryParam q: String?,
  ): String {
    val services = listOf("alpha", "bravo", "charlie")
    //   .getAllServices()
    //   .sortedBy { it.name }
    //   .filter { q.isNullOrBlank() || it.name.lowercase().contains(q.lowercase()) }
    //   .toSet()

    return buildHtml {
      services.map { service ->
        li("list-group-item cursor-default select-none px-4 py-2 text-left") {
          role = "option"
          attributes["data-autocomplete-value"] = service
          +service
        }
      }
    }
  }

  companion object {
    const val PATH = "/api/autocomplete/services"
  }
}
