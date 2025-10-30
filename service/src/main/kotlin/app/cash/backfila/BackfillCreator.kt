package app.cash.backfila

import app.cash.backfila.client.ConnectorProvider
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.persistence.DbService
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger

class BackfillCreator @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val connectorProvider: ConnectorProvider,
) {
  fun create(
    author: String,
    service: String,
    variant: String,
    request: CreateBackfillRequest,
  ): Id<DbBackfillRun> {
    logger.info { "Create backfill for `$service` by `$author`" }

    val num_threads = request.num_threads ?: 1
    val scan_size = request.scan_size ?: 1000L
    val batch_size = request.batch_size ?: 100L
    val dry_run = request.dry_run ?: true
    val extra_sleep_ms = request.extra_sleep_ms ?: 0

    validate(request, num_threads, scan_size, batch_size, extra_sleep_ms)

    val dbData = transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .variant(variant)
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist")
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
        registeredBackfill.id,
      )
    }

    val prepareBackfillResponse = prepare(dbData, service, request, dry_run)
    val partitions = prepareBackfillResponse.partitions

    val combinedParams = request.parameter_map.plus(prepareBackfillResponse.parameters)

    return transacter.transaction { session ->
      val backfillRun = DbBackfillRun(
        dbData.serviceId,
        dbData.registeredBackfillId,
        combinedParams,
        BackfillState.PAUSED,
        author,
        scan_size,
        batch_size,
        num_threads,
        request.backoff_schedule,
        dry_run,
        extra_sleep_ms,
      )
      session.save(backfillRun)

      for (partition in partitions) {
        val dbRunPartition = DbRunPartition(
          backfillRun.id,
          partition.partition_name,
          partition.backfill_range ?: KeyRange.Builder().build(),
          backfillRun.state,
          partition.estimated_record_count,
        )
        session.save(dbRunPartition)
      }

      backfillRun.id
    }
  }

  private fun prepare(
    dbData: DbData,
    service: String,
    request: CreateBackfillRequest,
    dry_run: Boolean,
  ): PrepareBackfillResponse {
    val client = connectorProvider.clientProvider(dbData.connectorType)
      .clientFor(service, dbData.connectorExtraData)

    val prepareBackfillResponse = try {
      client.prepareBackfill(
        PrepareBackfillRequest.Builder()
          .backfill_name(request.backfill_name)
          .range(
            KeyRange(
              request.pkey_range_start,
              request.pkey_range_end,
            ),
          )
          .parameters(request.parameter_map)
          .dry_run(dry_run)
          .build(),
      )
    } catch (e: Exception) {
      logger.info(e) { "PrepareBackfill on `$service` failed" }
      throw BadRequestException(
        "PrepareBackfill on `$service` failed: ${e.message}. " +
          "connectionData: ${client.connectionLogData()}",
        e,
      )
    }

    prepareBackfillResponse.error_message?.let {
      throw BadRequestException(
        "PrepareBackfill on `$service` failed: $it. " +
          "connectionData: ${client.connectionLogData()}",
      )
    }

    val partitions = prepareBackfillResponse.partitions
    if (partitions.isEmpty()) {
      throw BadRequestException(
        "PrepareBackfill returned no partitions. " +
          "connectionData: ${client.connectionLogData()}",
      )
    }
    if (partitions.any { it.partition_name == null }) {
      throw BadRequestException(
        "PrepareBackfill returned unnamed partitions. " +
          "connectionData: ${client.connectionLogData()}",
      )
    }
    if (partitions.distinctBy { it.partition_name }.size != partitions.size) {
      throw BadRequestException(
        "PrepareBackfill did not return distinct partition names:" +
          " ${partitions.map { it.partition_name }}. " +
          "connectionData: ${client.connectionLogData()}",
      )
    }

    return prepareBackfillResponse
  }

  private fun validate(
    request: CreateBackfillRequest,
    num_threads: Int,
    scan_size: Long,
    batch_size: Long,
    extra_sleep_ms: Long,
  ) {
    if (num_threads < 1) {
      throw BadRequestException("num_threads must be >= 1")
    }
    if (scan_size < 1) {
      throw BadRequestException("scan_size must be >= 1")
    }
    if (batch_size < 1) {
      throw BadRequestException("batch_size must be >= 1")
    }
    if (scan_size < batch_size) {
      throw BadRequestException("scan_size must be >= batch_size")
    }
    if (extra_sleep_ms < 0) {
      throw BadRequestException("extra_sleep_ms must be >= 0")
    }
    request.backoff_schedule?.let { schedule ->
      if (schedule.split(',').any { it.toLongOrNull() == null }) {
        throw BadRequestException("backoff_schedule must be a comma separated list of integers")
      }
    }
    for ((name, value) in request.parameter_map) {
      if (value.size > MAX_PARAMETER_VALUE_SIZE) {
        throw BadRequestException("parameter $name is too long (max $MAX_PARAMETER_VALUE_SIZE characters)")
      }
    }
  }

  private data class DbData(
    val serviceId: Id<DbService>,
    val connectorType: String,
    val connectorExtraData: String?,
    val registeredBackfillId: Id<DbRegisteredBackfill>,
  )

  companion object {
    private val logger = getLogger<BackfillCreator>()

    internal const val MAX_PARAMETER_VALUE_SIZE = 1000
  }
}
