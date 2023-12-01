package app.cash.backfila.client.s3

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.FinalizeBackfillConfig
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.s3.record.RecordStrategy

abstract class S3DatasourceBackfill<R : Any, P : Any> : Backfill {

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

  /**
   * Bucket where the S3 files are located
   */
  abstract fun getBucket(config: PrepareBackfillConfig<P>): String

  /**
   * Static element of the prefix, this is a helper used by the default getPrefix.
   * Warning: if getPrefix is overridden then this might not be used.
   */
  open val staticPrefix = ""

  /**
   * Calculates the prefix of S3 objects to process. Defaults to staticPrefix.
   */
  open fun getPrefix(config: PrepareBackfillConfig<P>): String = staticPrefix

  /**
   * Produces records from the S3 file.
   */
  abstract val recordStrategy: RecordStrategy<R>

  /**
   * Override this to do any work after the backfill completes.
   */
  open fun finalize(config: FinalizeBackfillConfig<P>) {}
}
