package app.cash.backfila.service.listener

import app.cash.backfila.service.persistence.DbBackfillRun
import misk.hibernate.Id

interface BackfillRunListener {
  fun runStarted(id: Id<DbBackfillRun>, user: String)
  fun runPaused(id: Id<DbBackfillRun>, user: String)
  fun runErrored(id: Id<DbBackfillRun>)
  fun runCompleted(id: Id<DbBackfillRun>)
  fun runCancelled(id: Id<DbBackfillRun>, user: String)
}
