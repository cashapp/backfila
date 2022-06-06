package app.cash.backfila.service.persistence

import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id

@Entity
@Table(name = "registered_parameters")
class DbRegisteredParameter() : DbUnsharded<DbRegisteredParameter>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbRegisteredParameter>

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "registered_backfill_id", updatable = false)
  lateinit var registered_backfill: DbRegisteredBackfill

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  @Column(nullable = false, updatable = false)
  lateinit var name: String

  @Column(nullable = true, updatable = false)
  var description: String? = null

  @Column(updatable = false)
  var required: Boolean = false

  constructor(
    registered_backfill: DbRegisteredBackfill,
    name: String,
    description: String?,
    required: Boolean,
  ) : this() {
    this.registered_backfill = registered_backfill
    this.name = name
    this.description = description
    this.required = required
  }
}
