package app.cash.backfila.client.s3.record

import okio.BufferedSource
import okio.ByteString

/**
 * For easy re-use we make handling records a strategy that is used by Backfills.
 */
interface RecordStrategy<R : Any> {
  /**
   * Calculates the byte offset of the start of the next record.
   * Cannot return 0 as you must make progress on the source.
   * You receive a peeked source so be careful how far ahead you read.
   */
  fun calculateNextRecordBytes(source: BufferedSource): Long

  /**
   * Converts a ByteString to records.
   */
  fun bytesToRecords(source: ByteString): List<R>
}
