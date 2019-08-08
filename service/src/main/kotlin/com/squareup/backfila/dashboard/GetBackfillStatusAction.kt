package com.squareup.backfila.dashboard

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.BackfillState
import com.squareup.backfila.service.DbBackfillRun
import com.squareup.backfila.service.DbRunInstance
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.logging.getLogger
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okio.ByteString
import javax.inject.Inject

data class UiInstance(
  val id: Long,
  val name: String,
  val state: BackfillState,
  val pkey_cursor: ByteString?,
  val pkey_start: ByteString?,
  val pkey_end: ByteString?,
  val precomputing_done: Boolean,
  val precomputing_pkey_cursor: ByteString?,
  val computed_scanned_record_count: Long,
  val computed_matching_record_count: Long,
  val backfilled_scanned_record_count: Long,
  val backfilled_matching_record_count: Long
)

data class GetBackfillStatusResponse(
  val service_name: String,
  val name: String,
  val state: BackfillState,
  val parameters: Map<String, ByteString>?,
  val batch_size: Long,
  val scan_size: Long,
  val dry_run: Boolean,
  val num_threads: Int,
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
          run.parameters(),
          run.batch_size,
          run.scan_size,
          run.dry_run,
          run.num_threads,
          run.instances(session, queryFactory).map { dbToUi(it) }
      )
    }
  }

  private fun dbToUi(instance: DbRunInstance) =
    UiInstance(
        instance.id.id,
        instance.instance_name,
        instance.run_state,
        instance.pkey_cursor,
        instance.pkey_range_start,
        instance.pkey_range_end,
        instance.precomputing_done,
        instance.precomputing_pkey_cursor,
        instance.computed_scanned_record_count,
        instance.computed_matching_record_count,
        instance.backfilled_scanned_record_count,
        instance.backfilled_matching_record_count
    )

  companion object {
    private val logger = getLogger<GetBackfillRunsAction>()
  }
}
