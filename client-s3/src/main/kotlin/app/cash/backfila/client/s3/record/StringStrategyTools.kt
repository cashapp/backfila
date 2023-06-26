package app.cash.backfila.client.s3.record

import okio.BufferedSource

object StringStrategyTools {
  /**
   * Optimized newline record byte calculator.
   */
  fun newlineByteCalculator(source: BufferedSource): Long {
    val index = source.indexOf(NEWLINE_BYTE)
    val bytes = if (index < 0) source.readByteString().size.toLong() else index + 1
    return bytes
  }

  /**
   * Optimized newline record byte calculator that treats consecutive newlines as a single newline.
   *
   * This is helpful when wanting to ignore empty lines.
   */
  fun multipleNewlineByteCalculator(source: BufferedSource): Long {
    var index = source.indexOf(NEWLINE_BYTE)
    if (index < 0) {
      // Reached the end of the file so return all the bytes.
      return source.readByteString().size.toLong()
    } else {
      source.skip(index + 1)
      while (!source.exhausted() && source.readByte() == NEWLINE_BYTE) {
        index += 1
      }
      return index + 1
    }
  }

  private const val NEWLINE_BYTE = '\n'.code.toByte()
}
