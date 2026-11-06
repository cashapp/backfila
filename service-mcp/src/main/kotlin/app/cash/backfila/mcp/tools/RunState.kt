package app.cash.backfila.mcp.tools

import app.cash.backfila.service.persistence.BackfillState
import kotlinx.serialization.Serializable

/** Mirrors [BackfillState] so tool schemas list the valid states. */
@Serializable
internal enum class RunState {
  PAUSED,
  RUNNING,
  COMPLETE,
  CANCELLED,
}

internal fun BackfillState.toRunState(): RunState = RunState.valueOf(name)
