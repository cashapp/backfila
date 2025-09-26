package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
import kotlinx.html.li
import kotlinx.html.stream.createHTML
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.constraint
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class SearchUsersAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {

  @Get("/api/services/{service}/variants/{variant}/users/search")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun searchUsersJson(
    @PathParam service: String,
    @PathParam variant: String,
    @QueryParam q: String? = null,
  ): List<String> {
    if (q.isNullOrBlank() || q.length < 2) {
      return emptyList()
    }

    return transacter.transaction { session ->
      val dbService = if (variant == "default") {
        queryFactory.newQuery<ServiceQuery>()
          .registryName(service)
          .uniqueResult(session) ?: throw BadRequestException("`$service` doesn't exist")
      } else {
        queryFactory.newQuery<ServiceQuery>()
          .registryName(service)
          .variant(variant)
          .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist")
      }

      // Get unique users who have created backfills for this service
      queryFactory.newQuery<BackfillRunQuery>()
        .serviceId(dbService.id)
        .constraint { backfillRunRoot ->
          like(backfillRunRoot.get("created_by_user"), "%$q%")
        }
        .list(session)
        .mapNotNull { it.created_by_user }
        .distinct()
        .sorted()
        .take(10) // Limit results for performance
    }
  }

  // HTML wrapper for Hotwire frontend
  @Get("/services/{service}/variants/{variant}/users/search")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(allowAnyUser = true)
  fun searchUsers(
    @PathParam service: String,
    @PathParam variant: String,
    @QueryParam q: String? = null,
  ): String {
    val users = searchUsersJson(service, variant, q)

    return createHTML().apply {
      users.forEach { user ->
        li("relative cursor-default select-none py-2 pl-3 pr-9 text-gray-900 hover:bg-indigo-600 hover:text-white") {
          attributes["role"] = "option"
          attributes["data-autocomplete-value"] = user
          +user
        }
      }
    }.finalize()
  }
}
