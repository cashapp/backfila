package app.cash.backfila.dashboard

import app.cash.backfila.service.listener.BackfillRunListener
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import javax.inject.Inject
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
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

class CancelBackfillAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val backfillRunListeners: Set<BackfillRunListener>,
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
) : WebAction {
  @Post("/backfill/cancel/{id}")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun cancel(
    @PathParam id: Long,
  ) {
    transacter.transaction { session ->
      val backfillRun = session.load<DbBackfillRun>(Id(id))

      // Check if backfill can be cancelled
      if (backfillRun.state == BackfillState.CANCELLED) {
        throw BadRequestException("Cannot cancel a ${backfillRun.state.name.lowercase()} backfill")
      }

      // Update state in run_partitions table
      backfillRun.setState(session, queryFactory, BackfillState.CANCELLED)

      // Log the cancellation event
      session.save(
        DbEventLog(
          backfillRun.id,
          partition_id = null,
          user = caller.get()?.user,
          type = DbEventLog.Type.STATE_CHANGE,
          message = "backfill cancelled",
        ),
      )
    }

    // Notify listeners
    backfillRunListeners.forEach { it.runCancelled(Id(id), caller.get()?.principal ?: "") }
  }
}
