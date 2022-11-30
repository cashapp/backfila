package app.cash.backfila.client.s3.record

import okio.BufferedSource
import okio.ByteString

/**
 * Produces single string records separated by newlines.
 */
class Utf8StringNewlineStrategy : RecordStrategy<String> {
  override fun calculateNextRecordBytes(source: BufferedSource): Long = StringStrategyTools.newlineByteCalculator(source)

  override fun bytesToRecords(source: ByteString): List<String> = source.utf8().split("\n")
}
