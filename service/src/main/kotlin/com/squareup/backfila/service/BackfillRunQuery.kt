package com.squareup.backfila.service

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Query

interface BackfillRunQuery : Query<DbBackfillRun> {
  @Constraint("id")
  fun id(id: Id<DbBackfillRun>): BackfillRunQuery

  @Constraint("service_id")
  fun serviceId(serviceId: Id<DbService>): BackfillRunQuery
}
