package app.cash.backfila.client.s3.record

import okio.BufferedSource
import okio.ByteString

/**
 * Produces single string records separated by newlines.
 */
class Utf8StringNewlineStrategy(
  private val ignoreBlankLines: Boolean = true,
) : RecordStrategy<String> {
  override fun calculateNextRecordBytes(source: BufferedSource): Long {
    return if (ignoreBlankLines) {
      StringStrategyTools.multipleNewlineByteCalculator(source)
    } else {
      StringStrategyTools.newlineByteCalculator(source)
    }
  }

  /**
   * Because the batch will always end with a newline unless it is the end of the file. If there is a newline at the end of
   * the file that newline will be considered the end of the file and no record will be processed for the empty string between
   * that last newline and the end of the file.
   *
   * If you are using `ignoreBlankLines` than this point is moot.
   */
  override fun bytesToRecords(source: ByteString): List<String> {
    return if (ignoreBlankLines) {
      source.utf8().split("\n").filter { it.isNotEmpty() }
    } else {
      source.utf8().removeSuffix("\n").split("\n")
    }
  }
}
