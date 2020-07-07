package app.cash.backfila.dashboard

import app.cash.backfila.client.ConnectorProvider
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.persistence.DbService
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.hibernate.loadOrNull
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject

enum class RangeCloneType {
  RESTART,
  CONTINUE,
  NEW
}

data class CloneBackfillRequest(
    // TODO move defaults to UI
    val scan_size: Long = 1000,
    val batch_size: Long = 100,
    val num_threads: Int = 5,
    val range_clone_type: RangeCloneType,
    val pkey_range_start: String? = null,
    val pkey_range_end: String? = null,
    // Parameters that go to the client service.
    val parameter_map: Map<String, ByteString> = mapOf(),
    val dry_run: Boolean = true,
    val backoff_schedule: String? = null,
    // Sleep that is added after every successful RunBatch.
    val extra_sleep_ms: Long = 0
)

data class CloneBackfillResponse(
    val id: Long
)

class CloneBackfillAction @Inject constructor(
    private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
    @BackfilaDb private val transacter: Transacter,
    private val queryFactory: Query.Factory,
    private val connectorProvider: ConnectorProvider
) : WebAction {

  @Post("/backfills/{id}/clone")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["users"])
  fun create(
      @PathParam id: Long,
      @RequestBody request: CloneBackfillRequest
  ): CloneBackfillResponse {
    // TODO check user has permissions for this service with access api

    logger.info { "Clone backfill $id by ${caller.get()?.user}" }

    if (request.num_threads < 1) {
      throw BadRequestException("num_threads must be >= 1")
    }
    if (request.scan_size < 1) {
      throw BadRequestException("scan_size must be >= 1")
    }
    if (request.batch_size < 1) {
      throw BadRequestException("batch_size must be >= 1")
    }
    if (request.scan_size < request.batch_size) {
      throw BadRequestException("scan_size must be >= batch_size")
    }
    if (request.extra_sleep_ms < 0) {
      throw BadRequestException("extra_sleep_ms must be >= 0")
    }
    request.backoff_schedule?.let { schedule ->
      if (schedule.split(',').any { it.toLongOrNull() == null }) {
        throw BadRequestException("backoff_schedule must be a comma separated list of integers")
      }
    }

    val dbData = transacter.transaction { session ->
      val sourceBackfill = session.loadOrNull<DbBackfillRun>(Id(id))
          ?: throw BadRequestException("backfill `$id` doesn't exist")
      val dbService = sourceBackfill.service
      DbData(
          dbService.id,
          dbService.registry_name,
          dbService.connector,
          dbService.connector_extra_data,
          sourceBackfill.registered_backfill_id,
          sourceBackfill.registered_backfill.name
      )
    }

    val client = connectorProvider.clientProvider(dbData.connectorType)
        .clientFor(dbData.serviceName, dbData.connectorExtraData)
    val prepareBackfillResponse = try {
      client.prepareBackfill(
          PrepareBackfillRequest(
              dbData.registeredBackfillId.toString(),
              dbData.backfillName,
              KeyRange(
                  request.pkey_range_start?.encodeUtf8(),
                  request.pkey_range_end?.encodeUtf8()
              ),
              request.parameter_map,
              request.dry_run
          )
      )
    } catch (e: Exception) {
      logger.info(e) { "PrepareBackfill on `${dbData.serviceName}` failed" }
      throw BadRequestException("PrepareBackfill on `${dbData.serviceName}` failed: ${e.message}", e)
    }
    val partitions = prepareBackfillResponse.partitions
    if (partitions.isEmpty()) {
      throw BadRequestException("PrepareBackfill returned no partitions")
    }
    if (partitions.any { it.partition_name == null }) {
      throw BadRequestException("PrepareBackfill returned unnamed partitions")
    }
    if (partitions.distinctBy { it.partition_name }.size != partitions.size) {
      throw BadRequestException("PrepareBackfill did not return distinct partition names:" +
          " ${partitions.map { it.partition_name }}")
    }

    val combinedParams = request.parameter_map.plus(prepareBackfillResponse.parameters)

    val backfillRunId = transacter.transaction { session ->
      val backfillRun = DbBackfillRun(
          dbData.serviceId,
          dbData.registeredBackfillId,
          combinedParams,
          BackfillState.PAUSED,
          caller.get()?.user,
          request.scan_size,
          request.batch_size,
          request.num_threads,
          request.backoff_schedule,
          request.dry_run,
          request.extra_sleep_ms
      )
      session.save(backfillRun)

      if (request.range_clone_type == RangeCloneType.NEW) {
        for (partition in partitions) {
          val dbRunPartition = DbRunPartition(
              backfillRun.id,
              partition.partition_name,
              partition.backfill_range ?: KeyRange.Builder().build(),
              backfillRun.state,
              partition.estimated_record_count
          )
          session.save(dbRunPartition)
        }
      } else {
        // Verify partitions match source backfill, use per partition ranges.
        val sourceBackfill = session.load<DbBackfillRun>(Id(id))
        // Source partitions have to match new partitions.
        val sourcePartitions = sourceBackfill.partitions(session, queryFactory)
        if (partitions.map { it.partition_name }.toSet() != sourcePartitions.map { it.partition_name }.toSet()) {
          throw BadRequestException("Can't clone backfill ranges from `$id`, newly computed partitions don't match.")
        }

        for (sourcePartition in sourcePartitions) {
          val dbRunPartition = DbRunPartition(
              backfillRun.id,
              sourcePartition.partition_name,
              sourcePartition.backfillRange(),
              backfillRun.state,
              sourcePartition.estimated_record_count
          )
          // Copy the cursor if continuing, otherwise just leave blank to start from beginning.
          if (request.range_clone_type == RangeCloneType.CONTINUE) {
            dbRunPartition.pkey_cursor = sourcePartition.pkey_cursor
          }
          session.save(dbRunPartition)
        }
      }

      backfillRun.id
    }

    return CloneBackfillResponse(backfillRunId.id)
  }

  data class DbData(
      val serviceId: Id<DbService>,
      val serviceName: String,
      val connectorType: String,
      val connectorExtraData: String?,
      val registeredBackfillId: Id<DbRegisteredBackfill>,
      val backfillName: String
  )

  companion object {
    private val logger = getLogger<CloneBackfillAction>()
  }
}
