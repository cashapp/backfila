package com.squareup.backfila.service

import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
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

  @Column(nullable = false)
  lateinit var connector: String

  @Column(columnDefinition = "mediumtext")
  var connector_extra_data: String? = null

  @Column
  var slack_channel: String? = null

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  constructor(
    registry_name: String,
    connector: String,
    connector_extra_data: String?,
    slack_channel: String?
  ) : this() {
    this.registry_name = registry_name
    this.connector = connector
    this.connector_extra_data = connector_extra_data
    this.slack_channel = slack_channel
  }
}