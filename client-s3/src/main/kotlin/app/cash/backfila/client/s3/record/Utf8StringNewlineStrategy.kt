package app.cash.backfila.client.s3.record

import okio.BufferedSource
import okio.ByteString

/**
 * Produces single string records separated by newlines.
 */
class Utf8StringNewlineStrategy(
  private val ignoreBlankLines: Boolean = true,
) : RecordStrategy<String> {
  override fun calculateNextRecordBytes(source: BufferedSource): Long = StringStrategyTools.newlineByteCalculator(source)

  override fun bytesToRecords(source: ByteString): List<String> {
    val lines = source.utf8().split("\n")
    if (ignoreBlankLines) {
      return lines.filter { it.isNotEmpty() }
    }
    return lines
  }
}
