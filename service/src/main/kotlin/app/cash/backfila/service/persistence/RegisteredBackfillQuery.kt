package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Query

interface RegisteredBackfillQuery : Query<DbRegisteredBackfill> {
  @Constraint("service_id")
  fun serviceId(serviceId: Id<DbService>): RegisteredBackfillQuery

  @Constraint("id", Operator.IN)
  fun idIn(backfillRunIds: Collection<Id<DbRegisteredBackfill>>): RegisteredBackfillQuery

  @Constraint("name")
  fun name(name: String): RegisteredBackfillQuery

  @Constraint("active", Operator.IS_NOT_NULL)
  fun active(): RegisteredBackfillQuery

  @Constraint("active", Operator.IS_NULL)
  fun notActive(): RegisteredBackfillQuery

  @Order("name")
  fun orderByName(): RegisteredBackfillQuery
}
