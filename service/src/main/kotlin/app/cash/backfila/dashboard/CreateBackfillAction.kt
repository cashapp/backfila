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
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
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

data class CreateBackfillRequest(
  val backfill_name: String,
  // TODO move defaults to UI
  val scan_size: Long = 1000,
  val batch_size: Long = 100,
  val num_threads: Int = 5,
  val pkey_range_start: String? = null,
  val pkey_range_end: String? = null,
  // Parameters that go to the client service.
  val parameter_map: Map<String, ByteString> = mapOf(),
  val dry_run: Boolean = true,
  val backoff_schedule: String? = null,
  // Sleep that is added after every successful RunBatch.
  val extra_sleep_ms: Long = 0
)

data class CreateBackfillResponse(
  val id: Long
)

class CreateBackfillAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val connectorProvider: ConnectorProvider
) : WebAction {

  @Post("/services/{service}/create")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["users"])
  fun create(
    @PathParam service: String,
    @RequestBody request: CreateBackfillRequest
  ): CreateBackfillResponse {
    // TODO check user has permissions for this service with access api

    logger.info { "Create backfill for $service by ${caller.get()?.user}" }

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
      val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(service)
          .uniqueResult(session) ?: throw BadRequestException("`$service` doesn't exist")
      val registeredBackfill = queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .name(request.backfill_name)
          .active()
          .uniqueResult(session)
          ?: throw BadRequestException("`${request.backfill_name}` doesn't exist")
      logger.info {
        "Found registered backfill for `$service`::`${request.backfill_name}`" +
            " [id=${registeredBackfill.id}]"
      }
      DbData(
          dbService.id,
          dbService.connector,
          dbService.connector_extra_data,
          registeredBackfill.id
      )
    }

    val client = connectorProvider.clientProvider(dbData.connectorType)
        .clientFor(service, dbData.connectorExtraData)
    val prepareBackfillResponse = try {
      client.prepareBackfill(
          PrepareBackfillRequest(
              dbData.registeredBackfillId.toString(),
              request.backfill_name,
              KeyRange(
                  request.pkey_range_start?.encodeUtf8(),
                  request.pkey_range_end?.encodeUtf8()
              ),
              request.parameter_map,
              request.dry_run
          )
      )
    } catch (e: Exception) {
      logger.info(e) { "PrepareBackfill on `$service` failed" }
      throw BadRequestException("PrepareBackfill on `$service` failed: " + e.message, e)
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

      backfillRun.id
    }

    return CreateBackfillResponse(backfillRunId.id)
  }

  data class DbData(
    val serviceId: Id<DbService>,
    val connectorType: String,
    val connectorExtraData: String?,
    val registeredBackfillId: Id<DbRegisteredBackfill>
  )

  companion object {
    private val logger = getLogger<CreateBackfillAction>()
  }
}
