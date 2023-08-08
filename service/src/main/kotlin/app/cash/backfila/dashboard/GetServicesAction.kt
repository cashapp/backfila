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
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger

class GetServicesAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {

  data class UiService(
    val name: String,
    val flavors: List<String?>,
    val running_backfills: Int,
  )

  data class GetServicesResponse(
    val services: List<UiService>,
  )

  @Get("/services")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun services(): GetServicesResponse {
    // TODO use caller user and registry API (cache result?) to determine visible services by Owner role
    // Then have services/all ? to see all

    val services = transacter.transaction { session ->
      val flavorsByService = queryFactory.newQuery<ServiceQuery>()
        .orderByName()
        .list(session)
        .groupBy { it.registry_name }

      val runningByService = queryFactory.newQuery<BackfillRunQuery>()
        .state(BackfillState.RUNNING)
        .list(session)
        .groupBy { it.service_id }

      flavorsByService.keys.map { registry_name ->
        UiService(
          name = registry_name,
          flavors = flavorsByService[registry_name]!!.map { serviceFlavor -> serviceFlavor.flavor },
          running_backfills = flavorsByService[registry_name]!!.sumOf { flavoredService ->
            runningByService[flavoredService.id]?.size ?: 0
          },
        )
      }
    }
    return GetServicesResponse(services)
  }

  companion object {
    private val logger = getLogger<GetServicesAction>()
  }
}
