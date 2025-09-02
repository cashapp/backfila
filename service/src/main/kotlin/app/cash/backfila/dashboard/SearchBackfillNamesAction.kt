package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class SearchBackfillNamesAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get("/services/{service}/variants/{variant}/backfill-names/search")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(allowAnyUser = true)
  fun searchBackfillNames(
    @PathParam service: String,
    @PathParam variant: String,
    @QueryParam q: String? = null,
  ): String {
    if (q.isNullOrBlank() || q.length < 2) {
      return ""
    }

    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .variant(variant)
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist")

      val backfillNames = queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(dbService.id)
        .list(session)
        .map { it.name }
        .distinct()
        .filter { it.contains(q, ignoreCase = true) }
        .sorted()
        .take(10) // Limit results for performance

      createHTML().apply {
        backfillNames.forEach { name ->
          li("relative cursor-default select-none py-2 pl-3 pr-9 text-gray-900 hover:bg-indigo-600 hover:text-white") {
            attributes["role"] = "option"
            attributes["data-autocomplete-value"] = name
            +name
          }
        }
      }.finalize()
    }
  }
}
