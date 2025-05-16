package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Query

interface EventLogQuery : Query<DbEventLog> {
  @Constraint("backfill_run_id")
  fun backfillRunId(backfillRunId: Id<DbBackfillRun>): EventLogQuery

  @Order("id", asc = false)
  fun orderByIdDesc(): EventLogQuery

  @Constraint("backfill_run_id", Operator.IN)
  fun backfillRunIdIn(backfillRunIds: Collection<Id<DbBackfillRun>>): EventLogQuery

  @Constraint("type")
  fun type(type: DbEventLog.Type): EventLogQuery

  @Order("updated_at", asc = false)
  fun orderByUpdatedAtDesc(): EventLogQuery
}
