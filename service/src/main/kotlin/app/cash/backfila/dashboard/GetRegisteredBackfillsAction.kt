package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import jakarta.inject.Inject
import java.time.Instant
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

data class RegisteredBackfill(
  val name: String,
  val parameterNames: List<String>,
  val createdAt: Instant,
  val updatedAt: Instant,
)
data class GetRegisteredBackfillsResponse(val backfills: List<RegisteredBackfill>)

class GetRegisteredBackfillsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get("/services/{service}/variants/{variant}/registered-backfills")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun backfills(
    @PathParam service: String,
    @PathParam variant: String,
  ): GetRegisteredBackfillsResponse {
    return getRegisteredBackfills(service, variant)
  }

  private fun getRegisteredBackfills(service: String, variant: String): GetRegisteredBackfillsResponse {
    val backfills = transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .variant(variant)
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist")
      val backfills = queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(dbService.id)
        .active()
        .orderByName()
        .list(session)

      // Include obsolete registrations so configuration changes and re-registration do not
      // reset the first time Backfila saw a backfill for this service and variant.
      @Suppress("UNCHECKED_CAST")
      val registrationTimes = session.hibernateSession.createQuery(
        """
        select name, min(created_at)
        from DbRegisteredBackfill
        where service_id = :serviceId
        group by name
        """,
      ).setParameter("serviceId", dbService.id)
        .list() as List<Array<Any>>
      val firstRegisteredAt = registrationTimes.associate { it[0] as String to it[1] as Instant }

      backfills.map {
        RegisteredBackfill(
          it.name,
          it.parameterNames(),
          firstRegisteredAt.getValue(it.name),
          it.created_at,
        )
      }
    }
    return GetRegisteredBackfillsResponse(backfills)
  }
}
