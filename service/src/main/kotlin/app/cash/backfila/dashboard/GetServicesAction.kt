package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger

class GetServicesAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {

  data class UiService(
    val name: String,
    val running_backfills: Int
  )

  data class GetServicesResponse(
    val services: List<UiService>
  )

  @Get("/services")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun services(): GetServicesResponse {
    // TODO use caller user and registry API (cache result?) to determine visible services by Owner role
    // Then have services/all ? to see all

    val services = transacter.transaction { session ->
      val services = queryFactory.newQuery<ServiceQuery>()
        .orderByName()
        .list(session)
      val runningByService = queryFactory.newQuery<BackfillRunQuery>()
        .state(BackfillState.RUNNING)
        .list(session)
        .groupBy { it.service_id }
      services.map {
        UiService(
          name = it.registry_name,
          running_backfills = runningByService[it.id]?.size ?: 0
        )
      }
    }
    return GetServicesResponse(services)
  }

  companion object {
    private val logger = getLogger<GetServicesAction>()
  }
}
