package com.squareup.backfila.api

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.DbRegisteredBackfill
import com.squareup.backfila.service.DbService
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import com.squareup.protos.cash.backfila.service.ConfigureServiceRequest
import com.squareup.protos.cash.backfila.service.ConfigureServiceResponse
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject

class ConfigureServiceAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {
  @Post("/configure_service")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  // TODO authenticate but any service
  @Unauthenticated
  fun configureService(@RequestBody request: ConfigureServiceRequest): ConfigureServiceResponse {
    val service = caller.get()!!.service!!

    logger.info { "Configuring service `$service` with ${request.backfills.size} backfills" }

    transacter.transaction { session ->
      var dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(service)
          .uniqueResult(session)
      if (dbService == null) {
        dbService = DbService(service, request.service_type)
        session.save(dbService)
      }

      // Add any missing backfills, update existing ones, and mark missing ones as deleted.
      val existingBackfills = queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .notDeletedInService()
          .list(session)
      logger.info { "Found ${existingBackfills.size} existing backfills for `$service`" }

      request.backfills
          .filter { e -> existingBackfills.none { it.name == e.name } }
          .forEach {
            logger.info { "New backfill for `$service`: `${it.name}`" }
            session.save(DbRegisteredBackfill(
                dbService.id, it.name, it.parameter_names, it.type_provided, it.type_consumed))
          }

      // Any existing backfills not in the current set should be marked deleted.
      val deleted = existingBackfills.filter { e -> request.backfills.none { it.name == e.name } }
      deleted.forEach {
        it.deleted_in_service = true
        logger.info { "Deleted backfill for `$service`: `${it.name}`" }
      }
    }

    logger.info { "Configured service `$service`" }

    return ConfigureServiceResponse()
  }

  companion object {
    private val logger = getLogger<ConfigureServiceAction>()
  }
}
