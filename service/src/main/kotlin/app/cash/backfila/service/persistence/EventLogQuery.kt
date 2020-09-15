package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Order
import misk.hibernate.Query

interface EventLogQuery : Query<DbEventLog> {
  @Constraint("backfill_run_id")
  fun backfillRunId(backfillRunId: Id<DbBackfillRun>): EventLogQuery

  @Order("id", asc = false)
  fun orderByIdDesc(): EventLogQuery
}
