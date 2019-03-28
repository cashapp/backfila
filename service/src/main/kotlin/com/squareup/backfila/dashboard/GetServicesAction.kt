package com.squareup.backfila.dashboard

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.ServiceQuery
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject

class GetServicesAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {

  data class GetServicesResponse(val services: List<String>)

  @Get("/services")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun services(): GetServicesResponse {
    // TODO use caller user and registry API (cache result?) to determine visible services by Owner role
    // Then have services/all ? to see all

    val services = transacter.transaction { session ->
      val services = queryFactory.newQuery<ServiceQuery>()
          .list(session)
      services.map { it.registry_name }
    }
    return GetServicesResponse(services)
  }

  companion object {
    private val logger = getLogger<GetServicesAction>()
  }
}
