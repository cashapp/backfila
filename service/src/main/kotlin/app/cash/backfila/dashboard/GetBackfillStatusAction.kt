package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import app.cash.backfila.service.persistence.DbRunPartition
import app.cash.backfila.service.persistence.EventLogQuery
import java.time.Instant
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.hibernate.newQuery
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

data class UiPartition(
  val id: Long,
  val name: String,
  val state: BackfillState,
  val pkey_cursor: String?,
  val pkey_start: String?,
  val pkey_end: String?,
  val precomputing_done: Boolean,
  val precomputing_pkey_cursor: String?,
  val computed_scanned_record_count: Long,
  val computed_matching_record_count: Long,
  val backfilled_scanned_record_count: Long,
  val backfilled_matching_record_count: Long,
  val scanned_records_per_minute: Long?,
  val matching_records_per_minute: Long?
)

data class UiEventLog(
  val occurred_at: Instant,
  val type: DbEventLog.Type,
  val user: String?,
  val partition_name: String?,
  val message: String,
  val extra_data: String?
)

data class GetBackfillStatusResponse(
  val service_name: String,
  val name: String,
  val state: BackfillState,
  val parameters: Map<String, String>?,
  val batch_size: Long,
  val scan_size: Long,
  val dry_run: Boolean,
  val num_threads: Int,
  val created_at: Instant,
  val created_by_user: String?,
  val extra_sleep_ms: Long,
  val backoff_schedule: String?,
  val partitions: List<UiPartition>,
  val event_logs: List<UiEventLog>
)

class GetBackfillStatusAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {
  @Get("/backfills/{id}/status")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["users"])
  fun status(
    @PathParam id: Long
  ): GetBackfillStatusResponse {
    return transacter.transaction { session ->
      val run = session.loadOrNull<DbBackfillRun>(Id(id))
        ?: throw BadRequestException("backfill $id doesn't exist")
      val partitions = run.partitions(session, queryFactory)
      GetBackfillStatusResponse(
        run.service.registry_name,
        run.registered_backfill.name,
        run.state,
        run.parameters()?.mapValues { (_, v) -> v.utf8() },
        run.batch_size,
        run.scan_size,
        run.dry_run,
        run.num_threads,
        run.created_at,
        run.created_by_user,
        run.extra_sleep_ms,
        run.backoff_schedule,
        partitions.map { dbToUi(it) },
        events(session, run, partitions)
      )
    }
  }

  private fun dbToUi(partition: DbRunPartition) =
    UiPartition(
      partition.id.id,
      partition.partition_name,
      partition.run_state,
      partition.pkey_cursor?.utf8(),
      partition.pkey_range_start?.utf8(),
      partition.pkey_range_end?.utf8(),
      partition.precomputing_done,
      partition.precomputing_pkey_cursor?.utf8(),
      partition.computed_scanned_record_count,
      partition.computed_matching_record_count,
      partition.backfilled_scanned_record_count,
      partition.backfilled_matching_record_count,
      partition.scanned_records_per_minute,
      partition.matching_records_per_minute
    )

  private fun events(
    session: Session,
    run: DbBackfillRun,
    partitions: List<DbRunPartition>
  ): List<UiEventLog> {
    val partitionsById = partitions.associateBy { it.id }
    return queryFactory.newQuery<EventLogQuery>()
      .backfillRunId(run.id)
      .orderByIdDesc()
      .apply { maxRows = 50 }
      .list(session)
      .map {
        UiEventLog(
          it.created_at,
          it.type,
          it.user,
          partitionsById[it.partition_id]?.partition_name,
          it.message,
          it.extra_data
        )
      }
  }
}
