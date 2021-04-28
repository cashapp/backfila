package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import wisp.logging.getLogger
import javax.inject.Inject

// These values correspond to those in CreateBackfillAction. Only non null values are updated.
data class UpdateBackfillRequest(
  val scan_size: Long? = null,
  val batch_size: Long? = null,
  val num_threads: Int? = null,
  val backoff_schedule: String? = null,
  val extra_sleep_ms: Long? = null
)

class UpdateBackfillResponse

class UpdateBackfillAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter
) : WebAction {

  @Post("/backfills/{id}/update")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["users"])
  fun update(
    @PathParam id: Long,
    @RequestBody request: UpdateBackfillRequest
  ): UpdateBackfillResponse {
    // TODO check user has permissions for this service with access api

    logger.info { "Update backfill $id by ${caller.get()?.user} $request" }

    transacter.transaction { session ->
      val run = session.loadOrNull<DbBackfillRun>(Id(id))
        ?: throw BadRequestException("backfill $id doesn't exist")
      logger.info {
        "Found backfill $id for `${run.registered_backfill.service.registry_name}`" +
          "::`${run.registered_backfill.name}`"
      }

      val newScanSize = request.scan_size ?: run.scan_size
      val newBatchSize = request.batch_size ?: run.batch_size
      if (newScanSize < 1) {
        throw BadRequestException("scan_size must be >= 1")
      }
      if (newBatchSize < 1) {
        throw BadRequestException("batch_size must be >= 1")
      }
      if (newScanSize < newBatchSize) {
        throw BadRequestException("scan_size must be >= batch_size")
      }
      val changesLog = mutableListOf<String>()

      if (run.scan_size != newScanSize) {
        changesLog += "scan_size ${run.scan_size}->$newScanSize"
        run.scan_size = newScanSize
      }
      if (run.batch_size != newBatchSize) {
        changesLog += "batch_size ${run.batch_size}->$newBatchSize"
        run.batch_size = newBatchSize
      }
      run.batch_size = newBatchSize

      if (request.num_threads != null) {
        if (request.num_threads < 1) {
          throw BadRequestException("num_threads must be >= 1")
        }
        if (run.num_threads != request.num_threads) {
          changesLog += "num_threads ${run.num_threads}->${request.num_threads}"
          run.num_threads = request.num_threads
        }
      }

      if (request.extra_sleep_ms != null) {
        if (request.extra_sleep_ms < 0) {
          throw BadRequestException("extra_sleep_ms must be >= 0")
        }
        if (run.extra_sleep_ms != request.extra_sleep_ms) {
          changesLog += "extra_sleep_ms ${run.extra_sleep_ms}->${request.extra_sleep_ms}"
          run.extra_sleep_ms = request.extra_sleep_ms
        }
      }

      request.backoff_schedule?.let { schedule ->
        if (request.backoff_schedule.isEmpty()) {
          changesLog += "backoff_schedule ${run.backoff_schedule}->"
          run.backoff_schedule = null
          return@let
        }
        if (schedule.split(',').any { it.toLongOrNull() == null }) {
          throw BadRequestException("backoff_schedule must be a comma separated list of integers")
        }
        if (run.backoff_schedule != request.backoff_schedule) {
          changesLog += "backoff_schedule ${run.backoff_schedule}->${request.backoff_schedule}"
          run.backoff_schedule = request.backoff_schedule
        }
        run.backoff_schedule = request.backoff_schedule
      }

      session.save(
        DbEventLog(
          run.id,
          partition_id = null,
          user = caller.get()!!.principal,
          type = DbEventLog.Type.CONFIG_CHANGE,
          message = "updated settings: " + changesLog.joinToString(", ")
        )
      )
    }

    return UpdateBackfillResponse()
  }

  companion object {
    private val logger = getLogger<UpdateBackfillAction>()
  }
}
