package app.cash.backfila.client.s3.shim

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.EMPTY
import okio.FileSystem
import okio.Path.Companion.toPath

@Singleton
class FakeS3Service @Inject constructor() : S3Service {
  private val files = mutableMapOf<Pair<String, String>, ByteString>()

  /**
   * Loads a whole directory of resource files into FakeS3Service
   */
  fun loadResourceDirectory(resourcePath: String) {
    val root = File(javaClass.classLoader.getResource(resourcePath).file)
    root.walk().maxDepth(1).forEach {
      require(it.isDirectory) // The first level is the bucket level, so they all must be directories.
      if (it != root) { // We don't want to process the root.
        addBucketFiles(it)
      }
    }
  }

  private fun addBucketFiles(bucketDirectory: File) {
    bucketDirectory.walk().forEach {
      if (!it.isDirectory) {
        val key = it.path.removePrefix(bucketDirectory.path).removePrefix("/")
        add(bucketDirectory.name, key, FileSystem.SYSTEM.read(it.path.toPath()) { readByteString() })
      }
    }
  }

  fun add(bucket: String, key: String, fileContent: ByteString) {
    files[bucket to key] = fileContent
  }

  override fun listFiles(bucket: String, keyPrefix: String): List<String> = files.keys
    .filter { (fileBucket, key) -> fileBucket == bucket && key.startsWith(keyPrefix) }
    .map { it.second }

  override fun getFileStreamStartingAt(bucket: String, key: String, start: Long): BufferedSource {
    val remainder = files[bucket to key]?.substring(start.toInt()) ?: EMPTY
    assert(remainder.size != 0) { "Amazon throws 'The requested range is not satisfiable'" }
    return Buffer().write(remainder)
  }

  override fun getWithSeek(
    bucket: String,
    key: String,
    seekStart: Long,
    seekEnd: Long,
  ): ByteString {
    /*
      S3's `withRange` has an inclusive end, so replicate that behaviour here.
      This will also fail if bytes are requested after the file end which Amazon does not do. However,
      this holds us to a higher standard to hopefully discover unintended behaviour.
    */
    val s3SeekEnd = seekEnd.toInt() + 1
    return files[bucket to key]?.substring(seekStart.toInt(), s3SeekEnd) ?: EMPTY
  }

  override fun getFileSize(bucket: String, key: String): Long =
    files[bucket to key]?.size?.toLong() ?: 0L
}
