package com.squareup.backfila.service

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Order
import misk.hibernate.Query

interface RunInstanceQuery : Query<DbRunInstance> {
  @Constraint("backfill_run_id")
  fun backfillRunId(backfillRunId: Id<DbBackfillRun>): RunInstanceQuery

  @Order("instance_name")
  fun orderByName(): RunInstanceQuery
}
