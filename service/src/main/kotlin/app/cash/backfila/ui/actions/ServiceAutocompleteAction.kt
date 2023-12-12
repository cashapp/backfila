package app.cash.backfila.ui.actions

import app.cash.backfila.dashboard.GetServicesAction
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
class ServiceAutocompleteAction @Inject constructor(
  private val getServicesAction: GetServicesAction,
) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun get(
    @QueryParam q: String?,
  ): String {
    val services = getServicesAction.services().services

    return buildHtml {
      services.flatMap { service ->
        service.variants.map { variant -> "${service.name}/$variant" to service }
      }.filter { (path, _) ->
        q.isNullOrBlank() || path.lowercase().contains(q.lowercase())
      }.map { (path, service) ->
        li("list-group-item cursor-default select-none px-4 py-2 text-left") {
          role = "option"
          attributes["data-autocomplete-value"] = path

          // Don't include default variant in label, only for unique variants
          val label = if (path.split("/").last() == "default") service.name else path
          +"""$label (${service.running_backfills})"""
        }
      }
    }
  }

  companion object {
    const val PATH = "/api/autocomplete/services"
  }
}
