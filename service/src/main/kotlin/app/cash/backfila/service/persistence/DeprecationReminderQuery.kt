package app.cash.backfila.service.persistence

import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Order
import misk.hibernate.Query

interface DeprecationReminderQuery : Query<DbDeprecationReminder> {
  @Constraint(path = "registered_backfill_id")
  fun registeredBackfillId(id: Id<DbRegisteredBackfill>): DeprecationReminderQuery

  @Order(path = "created_at", asc = false)
  fun orderByCreatedAtDesc(): DeprecationReminderQuery
}
