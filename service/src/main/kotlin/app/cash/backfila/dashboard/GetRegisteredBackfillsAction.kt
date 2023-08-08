package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
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

data class RegisteredBackfill(
  val name: String,
  val parameterNames: List<String>,
)
data class GetRegisteredBackfillsResponse(val backfills: List<RegisteredBackfill>)

class GetRegisteredBackfillsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get("/services/{service}/registered-backfills")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun backfills(
    @PathParam service: String,
    @QueryParam flavor: String? = null,
  ): GetRegisteredBackfillsResponse {
    val backfills = transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .flavor(flavor)
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$flavor` doesn't exist")
      val backfills = queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(dbService.id)
        .active()
        .orderByName()
        .list(session)
      backfills.map {
        RegisteredBackfill(
          it.name,
          it.parameterNames(),
        )
      }
    }
    return GetRegisteredBackfillsResponse(backfills)
  }
}
