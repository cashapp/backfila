package com.squareup.backfila.service

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Query

interface RegisteredBackfillQuery : Query<DbRegisteredBackfill> {
  @Constraint("service_id")
  fun serviceId(serviceId: Id<DbService>): RegisteredBackfillQuery

  @Constraint("deleted_in_service", Operator.IS_NULL)
  fun notDeletedInService(): RegisteredBackfillQuery

  @Constraint("deleted_in_service", Operator.IS_NOT_NULL)
  fun deletedInService(): RegisteredBackfillQuery
}
