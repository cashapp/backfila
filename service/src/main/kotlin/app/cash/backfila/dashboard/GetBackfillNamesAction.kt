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
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

data class GetBackfillNamesResponse(
  val backfill_names: List<String>,
)

class GetBackfillNamesAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get("/services/{service}/variants/{variant}/backfill-names")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun getBackfillNames(
    @PathParam service: String,
    @PathParam variant: String,
  ): GetBackfillNamesResponse {
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
        .sorted()

      GetBackfillNamesResponse(backfill_names = backfillNames)
    }
  }
}
