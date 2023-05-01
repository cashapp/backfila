package app.cash.backfila.client.sqldelight

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig

/**
 * S - RowSource - Maybe I can eliminate?
 * K - Key type (maybe can be compound later?)
 * R - Row type
 * P - Parameters type
 *
 */
abstract class SqlDelightDatasourceBackfill<S : SqlDelightRecordSource<K, R>, K : Any, R : Any, P : Any>(
  val rowSource: S,
) : Backfill {

  /**
   * Override this and throw an exception to prevent the backfill from being created.
   * This is also a good place to do any prep work before batches are run.
   */
  open fun validate(config: PrepareBackfillConfig<P>) {}

  /**
   * Called for each batch of records.
   * Override in a backfill to process all records in a batch.
   */
  open fun runBatch(records: List<@JvmSuppressWildcards R>, config: BackfillConfig<P>) {
    records.forEach { runOne(it, config) }
  }

  /**
   * Called for each record.
   * Override in a backfill to process one record at a time.
   */
  open fun runOne(record: R, config: BackfillConfig<P>) {
  }
}
