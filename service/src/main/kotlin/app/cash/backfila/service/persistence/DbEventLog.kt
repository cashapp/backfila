package app.cash.backfila.service.persistence

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Table
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id

@Entity
@Table(name = "event_logs")
class DbEventLog() : DbUnsharded<DbEventLog>, DbTimestampedEntity {

  enum class Type {
    STATE_CHANGE,
    CONFIG_CHANGE,
    ERROR,
  }

  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbEventLog>

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  @Column(nullable = false)
  lateinit var backfill_run_id: Id<DbBackfillRun>

  @Column
  var partition_id: Id<DbRunPartition>? = null

  /** User that triggered the event, null when the event was triggered by the service. */
  @Column
  var user: String? = null

  @Column
  @Enumerated(EnumType.STRING)
  lateinit var type: Type

  @Column(nullable = false)
  lateinit var message: String

  @Column(columnDefinition = "mediumtext")
  var extra_data: String? = null

  constructor(
    backfill_run_id: Id<DbBackfillRun>,
    partition_id: Id<DbRunPartition>? = null,
    user: String? = null,
    type: Type,
    message: String,
    extra_data: String? = null,
  ) : this() {
    this.backfill_run_id = backfill_run_id
    this.partition_id = partition_id
    this.user = user
    this.type = type
    this.message = message
    this.extra_data = extra_data
  }
}
