package app.cash.backfila.client.misk.spanner

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.KeyRange

abstract class SpannerBackfill<Param : Any> : Backfill {
  /**
   * A previously established connection to the DB that owns the table.
   */
  abstract val dbClient: DatabaseClient

  /**
   * The name of the table to be used as the source of the backfill.
   */
  abstract val tableName: String

  /**
   * A list of names of columns that make up the table's primary keys.
   * Only primary key columns that are strings are supported.
   */
  abstract val primaryKeyColumns: List<String>

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: BackfillConfig<Param>) {}

  /**
   * Run a backfill operation based on the provided range of primary keys from `tableName`.
   */
  abstract fun runBatch(range: KeyRange, config: BackfillConfig<Param>)
}
