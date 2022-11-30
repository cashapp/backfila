package app.cash.backfila.client.s3.shim

import okio.BufferedSource
import okio.ByteString

interface S3Service {
  /**
   * Returns a list of paths/keys for a prefix.
   */
  fun listFiles(bucket: String, pathPrefix: String): List<String>

  /**
   * Starts a streaming the file starting at a certain byte.
   * start is in bytes.
   */
  fun getFileStreamStartingAt(bucket: String, key: String, start: Long): BufferedSource

  /**
   * Obtains a file slice.
   * seekStart and seekEnd are in bytes.
   */
  fun getWithSeek(bucket: String, key: String, seekStart: Long, seekEnd: Long): ByteString

  /**
   * Returns the size in bytes.
   */
  fun getFileSize(bucket: String, key: String): Long
}
