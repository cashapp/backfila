package app.cash.backfila.client.s3.shim

import okio.BufferedSource
import okio.ByteString

interface S3Service {
  /**
   * Returns a list of paths/keys for a prefix.
   */
  fun listFiles(bucket: String, pathPrefix: String): List<String>

  /**
   * Starts a streaming the file starting at a certain byte and until a certain byte. This can more
   * readily stream large portions of the file.
   *
   * start and end are in bytes.
   */
  fun getFileChunkSource(bucket: String, key: String, start: Long, end: Long): BufferedSource

  /**
   * Obtains a file chunk ByteString. Use this for chunks that are not too long.
   *
   * start and end are in bytes.
   */
  fun getFileChunk(bucket: String, key: String, seekStart: Long, seekEnd: Long): ByteString

  /**
   * Returns the size in bytes.
   */
  fun getFileSize(bucket: String, key: String): Long
}
