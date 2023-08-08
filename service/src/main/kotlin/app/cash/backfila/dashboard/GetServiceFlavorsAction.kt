package app.cash.backfila.dashboard

import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_FLAVOR
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class GetServiceFlavorsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  data class UiFlavor(
    val name: String?,
    val running_backfills: Int,
  )

  data class GetServiceFlavorsResponse(
    val flavors: List<UiFlavor>,
  )

  @Get("/services/{service}/flavors")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun flavors(
    @PathParam service: String,
  ): GetServiceFlavorsResponse {
    val flavors = transacter.transaction { session ->
      val flavorsForService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .orderByFlavor()
        .list(session)

      val runningByFlavor = queryFactory.newQuery<BackfillRunQuery>()
        .serviceIdIn(flavorsForService.map { flavor -> flavor.id })
        .state(BackfillState.RUNNING)
        .list(session)
        .groupBy { it.service_id }

      flavorsForService.map {
        UiFlavor(
          name = it.flavor ?: RESERVED_FLAVOR,
          running_backfills = runningByFlavor[it.id]?.size ?: 0,
        )
      }
    }
    return GetServiceFlavorsResponse(flavors)
  }
}
