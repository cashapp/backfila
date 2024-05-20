package app.cash.backfila.dashboard

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

class GetServiceVariantsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {
  data class UiVariant(
    val name: String?,
    val running_backfills: Int,
  )

  data class GetServiceVariantsResponse(
    val variants: List<UiVariant>,
  )

  @Get("/services/{service}/variants")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(allowAnyUser = true)
  fun variants(
    @PathParam service: String,
  ): GetServiceVariantsResponse {
    val variants = transacter.transaction { session ->
      val variantsForService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .orderByVariant()
        .list(session)

      val runningByVariant = queryFactory.newQuery<BackfillRunQuery>()
        .serviceIdIn(variantsForService.map { variant -> variant.id })
        .state(BackfillState.RUNNING)
        .list(session)
        .groupBy { it.service_id }

      variantsForService.map {
        UiVariant(
          name = it.variant,
          running_backfills = runningByVariant[it.id]?.size ?: 0,
        )
      }
    }
    return GetServiceVariantsResponse(variants)
  }
}
