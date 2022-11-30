package app.cash.backfila.client.s3.record

import okio.BufferedSource

object StringStrategyTools {
  /**
   * Optimized newline record byte calculator.
   */
  fun newlineByteCalculator(source: BufferedSource): Long {
    val index = source.indexOf('\n'.code.toByte())
    val bytes = if (index < 0) source.readByteString().size.toLong() else index + 1
    return bytes
  }
}