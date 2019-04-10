package com.squareup.backfila.service

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Query

interface RunInstanceQuery : Query<DbRunInstance> {
  @Constraint("backfill_run_id")
  fun backfillRunId(backfillRunId: Id<DbBackfillRun>): RunInstanceQuery
}
