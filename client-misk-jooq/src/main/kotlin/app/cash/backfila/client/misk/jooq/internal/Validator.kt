package app.cash.backfila.client.misk.jooq.internal

import app.cash.backfila.client.misk.jooq.ByteStringSerializer
import app.cash.backfila.protos.clientservice.KeyRange
import misk.exceptions.BadRequestException

object Validator {
  /**
   * Validates a KeyRange by verifying that start or end can be parsed by the given serializer if
   * they are non-null.
   *
   * @throws BadRequestException if the KeyRange is invalid
   */
  fun validateRange(range: KeyRange?, serializer: ByteStringSerializer<*>) {
    if (range == null) {
      return
    }
    try {
      if (range.start != null) {
        serializer.fromByteString(range.start)
      }
    } catch (e: NumberFormatException) {
      throw BadRequestException("Start of range must be a number")
    }
    try {
      if (range.end != null) {
        serializer.fromByteString(range.end)
      }
    } catch (e: NumberFormatException) {
      throw BadRequestException("End of range must be a number")
    }
  }
}
