package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Query

interface BackfillRunQuery : Query<DbBackfillRun> {
  @Constraint("id")
  fun id(id: Id<DbBackfillRun>): BackfillRunQuery

  @Constraint("service_id")
  fun serviceId(serviceId: Id<DbService>): BackfillRunQuery

  @Constraint("service_id", Operator.IN)
  fun serviceIdIn(serviceIds: Collection<Id<DbService>>): BackfillRunQuery

  @Constraint("state")
  fun state(state: BackfillState): BackfillRunQuery

  @Constraint("state", Operator.NE)
  fun stateNot(state: BackfillState): BackfillRunQuery

  @Constraint("created_by_user", Operator.EQ)
  fun createdByUser(user: String): BackfillRunQuery

  @Order("id", asc = false)
  fun orderByIdDesc(): BackfillRunQuery

  @Order("updated_at", asc = false)
  fun orderByUpdatedAtDesc(): BackfillRunQuery

  @Constraint("soft_deleted", Operator.EQ)
  fun notSoftDeleted(softDeleted: Boolean = false): BackfillRunQuery
}
