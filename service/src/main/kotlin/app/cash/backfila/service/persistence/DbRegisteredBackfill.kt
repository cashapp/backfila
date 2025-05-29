package app.cash.backfila.service.persistence

import app.cash.backfila.protos.service.Parameter
import java.time.Clock
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.OrderBy
import javax.persistence.Table
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id

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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "service_id", updatable = false, insertable = false)
  lateinit var service: DbService

  @Column(nullable = false)
  lateinit var name: String

  /**
   * When the backfill is updated or deleted on the client service,
   * we stop showing it, but we keep it for historic references.
   *
   * Only one backfill for this service and name can be active, and it has the current config.
   * Others with the same service and name are obsolete.
   * A unique index ensures this and prevents races from different hosts from creating duplicates.
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
  @Deprecated("to be replaced with parameters")
  @Column(columnDefinition = "mediumtext")
  var parameter_names: String? = null

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "registered_backfill")
  @OrderBy("id")
  var parameters = mutableSetOf<DbRegisteredParameter>()

  @Column
  var type_provided: String? = null

  @Column
  var type_consumed: String? = null

  @Column
  var requires_approval: Boolean = false

  /**
   * Notes when this backfill should no longer be useful and the code should be deleted. This is
   * used to power removal reminders. It guarantees that reminders will not occur until after this
   * date. This defaults to as soon as possible if it is missing.
   */
  @Column
  var delete_by: Instant? = null

  @Column(nullable = true)
  var unit: String? = null

  constructor(
    service_id: Id<DbService>,
    name: String,
    parameters: List<Parameter>,
    type_provided: String?,
    type_consumed: String?,
    requires_approval: Boolean,
    delete_by: Instant?,
    unit: String? = null,
  ) : this() {
    this.service_id = service_id
    this.name = name
    if (parameters.isNotEmpty()) {
      this.parameter_names = parameters.joinToString(",") { it.name }
    }
    this.type_provided = type_provided
    this.type_consumed = type_consumed
    this.active = true
    this.requires_approval = requires_approval
    this.delete_by = delete_by
    this.unit = unit

    parameters.forEach {
      this.parameters.add(
        DbRegisteredParameter(
          this,
          it.name,
          it.description,
          it.required ?: false,
        ),
      )
    }
  }

  /** True if the variables configured by the client service are equal to what is stored. */
  fun equalConfig(other: DbRegisteredBackfill): Boolean {
    if (parameter_names != other.parameter_names) return false
    if (type_provided != other.type_provided) return false
    if (type_consumed != other.type_consumed) return false
    if (requires_approval != other.requires_approval) return false
    if (delete_by != other.delete_by) return false
    if (unit != other.unit) return false

    val ourParams = parameters.map { Parameter(it.name, it.description, it.required) }
    val otherParams = other.parameters.map { Parameter(it.name, it.description, it.required) }
    if (ourParams != otherParams) return false

    return true
  }

  fun deactivate(clock: Clock) {
    this.active = null
    this.deleted_in_service_at = clock.instant()
  }

  // TODO(mgersh): after backfilling the parameters table, drop parameter_names and start returning description as well
  fun parameterNames() = parameter_names?.split(",") ?: listOf()
}
