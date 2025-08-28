package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.ServiceQuery
import java.time.Instant
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

data class GetServiceDetailsResponse(
  val service_name: String,
  val variant: String,
  val connector: String,
  val connector_extra_data: String?,
  val slack_channel: String?,
  val created_at: Instant,
  val updated_at: Instant,
  val last_registered_at: Instant?,
)

class GetServiceDetailsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  @Get("/services/{service}/variants/{variant}/details")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun getServiceDetails(
    @PathParam service: String,
    @PathParam variant: String,
  ): GetServiceDetailsResponse {
    return transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .variant(variant)
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist")

      GetServiceDetailsResponse(
        service_name = dbService.registry_name,
        variant = dbService.variant,
        connector = dbService.connector,
        connector_extra_data = dbService.connector_extra_data,
        slack_channel = dbService.slack_channel,
        created_at = dbService.created_at,
        updated_at = dbService.updated_at,
        last_registered_at = dbService.last_registered_at,
      )
    }
  }
}
