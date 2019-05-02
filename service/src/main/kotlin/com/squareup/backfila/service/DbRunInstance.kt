package com.squareup.backfila.service

import com.squareup.protos.backfila.clientservice.KeyRange
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.DbUnsharded
import misk.hibernate.Id
import okio.ByteString
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.Table
import javax.persistence.Version

/**
 * Backfill runs can have many instances, e.g. one per database shard.
 * Each instance tracks cursors individually. They are also leased individually.
 * All instances of a run have the same running or paused state.
 */
@Entity
@Table(name = "run_instances")
class DbRunInstance() : DbUnsharded<DbRunInstance>, DbTimestampedEntity {
  @javax.persistence.Id
  @GeneratedValue
  override lateinit var id: Id<DbRunInstance>

  @Column(nullable = false)
  lateinit var backfill_run_id: Id<DbBackfillRun>

  @Column(nullable = false)
  lateinit var instance_name: String

  @Column
  override lateinit var created_at: Instant

  @Column
  override lateinit var updated_at: Instant

  @Column(nullable = false) @Version
  var version: Long = 0

  /**
   * State of the backfill run, kept in sync with backfill_runs to allow indexing
   * on this and the lease column.
   */
  @Column(nullable = false) @Enumerated(EnumType.STRING)
  lateinit var run_state: BackfillState

  @Column
  var lease_token: String? = null

  @Column
  var lease_expires_at: Instant? = null

  @Column
  var pkey_cursor: ByteString? = null

  @Column
  var pkey_range_end: ByteString? = null

  @Column
  var estimated_record_count: Long? = null

  /**
   * Cursor used to precompute the size of the data.
   * Precomputing is done when this equals pkey_range_end.
   **/
  @Column
  var precomputing_pkey_cursor: ByteString? = null

  /** How many records in the data set. Not correct until precomputing is done. */
  @Column
  var computed_record_count: Long? = null

  @Column
  var backfilled_record_count: Long? = null

  constructor(
    backfill_run_id: Id<DbBackfillRun>,
    instance_name: String,
    backfill_range: KeyRange,
    run_state: BackfillState
  ) : this() {
    this.backfill_run_id = backfill_run_id
    this.instance_name = instance_name
    this.pkey_cursor = backfill_range.start
    this.pkey_range_end = backfill_range.end
    this.run_state = run_state
  }
}
