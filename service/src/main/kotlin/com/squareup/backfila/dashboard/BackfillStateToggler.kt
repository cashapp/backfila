package com.squareup.backfila.dashboard

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.BackfillState
import com.squareup.backfila.service.BackfillState.PAUSED
import com.squareup.backfila.service.BackfillState.RUNNING
import com.squareup.backfila.service.DbBackfillRun
import com.squareup.backfila.service.SlackHelper
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.logging.getLogger
import javax.inject.Inject

class BackfillStateToggler @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val slackHelper: SlackHelper
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
            "backfill $id isn't $requiredCurrentState, can't move to state $desiredState")
      }
      run.setState(session, queryFactory, desiredState)
    }

    if (desiredState == RUNNING) {
      slackHelper.runStarted(Id(id), caller.user!!)
    } else {
      slackHelper.runPaused(Id(id), caller.user!!)
    }

    // TODO audit log event
  }

  companion object {
    private val logger = getLogger<BackfillStateToggler>()
  }
}
