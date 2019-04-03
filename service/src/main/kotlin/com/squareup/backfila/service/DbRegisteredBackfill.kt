package com.squareup.backfila.service

import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table

/**
 * Stores the set of backfills that a service has registered as runnable.
 */
@Entity
@Table(name = "registered_backfills")
class DbRegisteredBackfill() : DbUnsharded<DbRegisteredBackfill>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbRegisteredBackfill>

  @Column(nullable = false)
  lateinit var service_id: Id<DbService>

  @Column(nullable = false)
  lateinit var name: String

  /**
   * When the backfill is updated or deleted on the client service,
   * we stop showing it, but we keep it for historic references.
   *
   * Only one backfill for this service and name can be active, and it has the current config.
   * Others with the same service and name are obsolete.
   */
  @Column
  var active: Boolean? = null

  @Column
  var deleted_in_service_at: Instant? = null

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  // TODO(mgersh): this might want to be its own table
  @Column(columnDefinition = "blob")
  var parameter_names: String? = null

  @Column
  var type_provided: String? = null

  @Column
  var type_consumed: String? = null

  constructor(
    service_id: Id<DbService>,
    name: String,
    parameter_names: List<String>,
    type_provided: String?,
    type_consumed: String?
  ) : this() {
    this.service_id = service_id
    this.name = name
    this.parameter_names = parameter_names.joinToString(",")
    this.type_provided = type_provided
    this.type_consumed = type_consumed
    this.active = true
  }
}