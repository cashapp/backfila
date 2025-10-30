package app.cash.backfila.dashboard

import app.cash.backfila.service.listener.BackfillRunListener
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.BackfillState.PAUSED
import app.cash.backfila.service.persistence.BackfillState.RUNNING
import app.cash.backfila.service.persistence.DbBackfillRun
import app.cash.backfila.service.persistence.DbEventLog
import javax.inject.Inject
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.logging.getLogger

class BackfillStateToggler @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val backfillRunListeners: Set<BackfillRunListener>,
) {
  fun toggleRunningState(id: Long, caller: MiskCaller, desiredState: BackfillState) {
    val requiredCurrentState = when (desiredState) {
      PAUSED -> RUNNING
      RUNNING -> PAUSED
      else -> throw IllegalArgumentException("can only toggle to RUNNING or PAUSED")
    }

    transacter.transaction { session ->
      val run = session.loadOrNull<DbBackfillRun>(Id(id))
        ?: throw BadRequestException("backfill $id doesn't exist")
      logger.info {
        "Found backfill $id for `${run.registered_backfill.service.registry_name}`" +
          "::`${run.registered_backfill.name}`"
      }
      if (run.state != requiredCurrentState) {
        logger.info {
          "Backfill $id can't move to state $desiredState, " +
            "in state ${run.state}, requires $requiredCurrentState"
        }
        throw BadRequestException(
          "backfill $id isn't $requiredCurrentState, can't move to state $desiredState",
        )
      }
      run.setState(session, queryFactory, desiredState)

      val startedOrStopped = if (desiredState == RUNNING) "started" else "stopped"
      session.save(
        DbEventLog(
          run.id,
          partition_id = null,
          user = caller.principal,
          type = DbEventLog.Type.STATE_CHANGE,
          message = "backfill $startedOrStopped",
        ),
      )
    }

    if (desiredState == RUNNING) {
      backfillRunListeners.forEach { it.runStarted(Id(id), caller.principal) }
    } else {
      backfillRunListeners.forEach { it.runPaused(Id(id), caller.principal) }
    }
  }

  companion object {
    private val logger = getLogger<BackfillStateToggler>()
  }
}
