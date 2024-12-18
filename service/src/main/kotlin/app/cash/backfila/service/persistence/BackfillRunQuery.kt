package app.cash.backfila.service.persistence

import misk.hibernate.*

interface BackfillRunQuery : Query<DbBackfillRun> {
  @Constraint("id")
  fun id(id: Id<DbBackfillRun>): BackfillRunQuery

  @Constraint("service_id")
  fun serviceId(serviceId: Id<DbService>): BackfillRunQuery

  @Constraint("service_id", Operator.IN)
  fun serviceIdIn(serviceIds: Collection<Id<DbService>>): BackfillRunQuery

  @Constraint("state")
  fun state(state: BackfillState): BackfillRunQuery

  @Constraint("registered_backfill.name")
  fun backfillName(name: String): BackfillRunQuery

  @Constraint("created_by_user")
  fun createdByUser(user: String): BackfillRunQuery

  @Constraint("state", Operator.NE)
  fun stateNot(state: BackfillState): BackfillRunQuery

  @Order("id", asc = false)
  fun orderByIdDesc(): BackfillRunQuery
}
