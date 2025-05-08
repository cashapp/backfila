package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import javax.inject.Inject
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class SoftDeleteBackfillAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
) : WebAction {
  @Post("/backfill/delete/{id}")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(capabilities = ["users"])
  fun softDelete(
    @PathParam id: Long,
  ) {
    transacter.transaction { session ->
      val backfillRun = session.load<DbBackfillRun>(Id(id))

      // Only allow soft delete for COMPLETE or CANCELLED backfills
      if (backfillRun.state != BackfillState.COMPLETE && backfillRun.state != BackfillState.CANCELLED) {
        throw BadRequestException("Can only delete completed or cancelled backfills")
      }

      backfillRun.deleted_at = java.time.Instant.now()

      // Log the deletion event
      session.save(
        DbEventLog(
          backfillRun.id,
          partition_id = null,
          user = caller.get()?.principal ?: "",
          type = DbEventLog.Type.STATE_CHANGE,
          message = "backfill soft deleted",
        ),
      )
    }
  }
}
