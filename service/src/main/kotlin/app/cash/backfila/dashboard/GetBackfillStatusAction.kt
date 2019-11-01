package app.cash.backfila.dashboard

import app.cash.backfila.service.BackfilaDb
import app.cash.backfila.service.BackfillState
import app.cash.backfila.service.DbBackfillRun
import app.cash.backfila.service.DbRunInstance
import java.time.Instant
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

data class UiInstance(
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
  val instances: List<UiInstance>
)

class GetBackfillStatusAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {
  @Get("/backfills/{id}/status")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["eng"])
  fun status(
    @PathParam id: Long
  ): GetBackfillStatusResponse {
    return transacter.transaction { session ->
      val run = session.loadOrNull<DbBackfillRun>(Id(id))
          ?: throw BadRequestException("backfill $id doesn't exist")
      GetBackfillStatusResponse(
          run.service.registry_name,
          run.registered_backfill.name,
          run.state,
          run.parameters()?.mapValues { (k, v) -> v.utf8() },
          run.batch_size,
          run.scan_size,
          run.dry_run,
          run.num_threads,
          run.created_at,
          run.created_by_user,
          run.extra_sleep_ms,
          run.backoff_schedule,
          run.instances(session, queryFactory).map { dbToUi(it) }
      )
    }
  }

  private fun dbToUi(instance: DbRunInstance) =
    UiInstance(
        instance.id.id,
        instance.instance_name,
        instance.run_state,
        instance.pkey_cursor?.utf8(),
        instance.pkey_range_start?.utf8(),
        instance.pkey_range_end?.utf8(),
        instance.precomputing_done,
        instance.precomputing_pkey_cursor?.utf8(),
        instance.computed_scanned_record_count,
        instance.computed_matching_record_count,
        instance.backfilled_scanned_record_count,
        instance.backfilled_matching_record_count,
        instance.scanned_records_per_minute,
        instance.matching_records_per_minute
    )
}
