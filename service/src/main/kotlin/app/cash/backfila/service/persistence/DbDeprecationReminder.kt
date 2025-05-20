package app.cash.backfila.service.persistence

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import misk.hibernate.DbUnsharded
import misk.hibernate.Id

@Entity
@Table(name = "deprecation_reminders")
class DbDeprecationReminder(
  @Column(nullable = false)
  var registered_backfill_id: Id<DbRegisteredBackfill>,

  @Column(nullable = false)
  var message_last_user: Boolean = false,

  @Column(nullable = false)
  var repeated: Boolean = false,

  @Column(nullable = false)
  var created_at: Instant,
) : DbUnsharded<DbDeprecationReminder> {

  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbDeprecationReminder>
}
