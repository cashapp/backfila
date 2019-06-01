package com.squareup.backfila.api

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.DbRegisteredBackfill
import com.squareup.backfila.service.DbService
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import com.squareup.protos.backfila.service.ConfigureServiceRequest
import com.squareup.protos.backfila.service.ConfigureServiceResponse
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
import java.time.Clock
import javax.inject.Inject

class ConfigureServiceAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val clock: Clock
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

      // Add any missing backfills, update modified ones, and mark missing ones as deleted.
      val existingBackfills = queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .active()
          .list(session)
          .associateBy { it.name }
      logger.info { "Found ${existingBackfills.size} existing backfills for `$service`" }

      for (backfill in request.backfills) {
        val existingBackfill = existingBackfills[backfill.name]
        val newBackfill = DbRegisteredBackfill(
            dbService.id,
            backfill.name,
            backfill.parameter_names,
            backfill.type_provided,
            backfill.type_consumed,
            backfill.requires_approval == true
        )
        var save = false
        if (existingBackfill != null) {
          // Replace it only if the config has changed.
          if (!existingBackfill.equalConfig(newBackfill)) {
            existingBackfill.deactivate(clock)
            session.hibernateSession.flush()
            save = true
            logger.info { "Updated backfill config for `$service`: `${backfill.name}`" }
          } else {
            logger.info { "Backfill config unchanged for `$service`: `${backfill.name}`" }
          }
        } else {
          // Add the new backfill.
          logger.info { "New backfill for `$service`: `${backfill.name}`" }
          save = true
        }
        if (save) {
          session.save(newBackfill)
        }
      }

      // Any existing backfills not in the current set should be marked deleted.
      val deleted = existingBackfills.keys - request.backfills.map { it.name }
      deleted.forEach { name ->
        existingBackfills.getValue(name).deactivate(clock)
        logger.info { "Deleted backfill for `$service`: `$name`" }
      }
    }

    logger.info { "Configured service `$service`" }

    return ConfigureServiceResponse()
  }

  companion object {
    private val logger = getLogger<ConfigureServiceAction>()
  }
}
