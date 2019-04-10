package com.squareup.backfila.service

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Query

interface BackfillRunQuery : Query<DbBackfillRun> {
  @Constraint("service_id")
  fun serviceId(serviceId: Id<DbService>): BackfillRunQuery
}
