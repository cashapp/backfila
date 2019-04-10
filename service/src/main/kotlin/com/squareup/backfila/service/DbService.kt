package com.squareup.backfila.service

import com.squareup.protos.backfila.service.ServiceType
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Table

@Entity
@Table(name = "services")
class DbService() : DbUnsharded<DbService>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbService>

  @Column(nullable = false)
  lateinit var registry_name: String

  @Column(nullable = false) @Enumerated(EnumType.STRING)
  lateinit var service_type: ServiceType

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  constructor(registry_name: String, service_type: ServiceType) : this() {
    this.registry_name = registry_name
    this.service_type = service_type
  }
}