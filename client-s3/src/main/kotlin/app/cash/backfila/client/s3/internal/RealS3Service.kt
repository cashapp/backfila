package app.cash.backfila.client.s3.internal

import app.cash.backfila.client.s3.ForS3Backend
import app.cash.backfila.client.s3.shim.S3Service
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.GetObjectRequest
import javax.inject.Inject
import javax.inject.Singleton
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.buffer
import okio.source

@Singleton
class RealS3Service @Inject constructor(
  @ForS3Backend private val amazonS3: AmazonS3,
) : S3Service {

  override fun listFiles(bucket: String, keyPrefix: String): List<String> {
    val s3ObjectListing = amazonS3.listObjects(bucket, keyPrefix)
    val summaries = s3ObjectListing.objectSummaries
    return summaries.map { it.key }
  }

  override fun getFileSize(bucket: String, key: String): Long {
    val objectMetadata = amazonS3.getObjectMetadata(bucket, key)
    return objectMetadata.contentLength
  }

  override fun getFileStreamStartingAt(bucket: String, key: String, start: Long): BufferedSource {
    val s3Object = amazonS3.getObject(GetObjectRequest(bucket, key).withRange(start))
    return s3Object.objectContent.source().buffer()
  }

  override fun getWithSeek(bucket: String, key: String, seekStart: Long, seekEnd: Long): ByteString {
    val s3Object = amazonS3.getObject(GetObjectRequest(bucket, key).withRange(seekStart, seekEnd))
    return Buffer().run {
      s3Object.use {
        writeAll(it.objectContent.source())
        readByteString()
      }
    }
  }
}
