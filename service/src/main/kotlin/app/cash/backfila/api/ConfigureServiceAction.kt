package app.cash.backfila.api

import app.cash.backfila.client.ConnectorProvider
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbService
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger

class ConfigureServiceAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val clock: Clock,
  private val connectorProvider: ConnectorProvider,
) : WebAction {
  @Post("/configure_service")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  // TODO authenticate but any service
  @Unauthenticated
  fun configureService(@RequestBody request: ConfigureServiceRequest): ConfigureServiceResponse {
    val service = caller.get()!!.service!!

    logger.info { "Configuring service `$service` with ${request.backfills.size} backfills" }

    request.variant?.let {
      check(!request.variant.equals(RESERVED_VARIANT)) { "Cannot use a reserved variant name" }
      check(!request.variant.contains(WHITESPACE_REGEX)) { "Variant cannot contain whitespace" }
    }

    val variant = request.variant ?: RESERVED_VARIANT
    val clientProvider = connectorProvider.clientProvider(request.connector_type)
    // This tests that the extra data is valid, throwing an exception if invalid.
    clientProvider.validateExtraData(request.connector_extra_data)

    transacter.transaction { session ->
      val variantsForService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .list(session)

      var dbService = variantsForService.firstOrNull() { it.variant == variant }

      if (dbService == null) {
        check(variantsForService.size <= MAX_VARIANTS) { "Variant limit exceeded" }

        dbService = DbService(
          service,
          request.connector_type,
          request.connector_extra_data,
          request.slack_channel,
          variant,
          clock.instant(),
        )
        session.save(dbService)
      } else {
        dbService.connector = request.connector_type
        dbService.connector_extra_data = request.connector_extra_data
        dbService.slack_channel = request.slack_channel
        dbService.variant = variant
        dbService.last_registered_at = clock.instant()
      }

      // Add any missing backfills, update modified ones, and mark missing ones as deleted.
      val existingBackfills = queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(dbService.id)
        .active()
        .list(session)
        .associateBy { it.name }
      logger.info { "Found ${existingBackfills.size} existing backfills for `$service`-`${request.variant}`" }

      for (backfill in request.backfills) {
        val existingBackfill = existingBackfills[backfill.name]
        val newBackfill = DbRegisteredBackfill(
          dbService.id,
          backfill.name,
          backfill.parameters,
          backfill.type_provided,
          backfill.type_consumed,
          backfill.requires_approval == true,
          backfill.delete_by?.let(Instant::ofEpochMilli),
          backfill.unit,
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
          newBackfill.parameters.forEach { session.save(it) }
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
    const val RESERVED_VARIANT = "default"
    const val MAX_VARIANTS = 10
    private val WHITESPACE_REGEX = Regex("\\s")
  }
}
