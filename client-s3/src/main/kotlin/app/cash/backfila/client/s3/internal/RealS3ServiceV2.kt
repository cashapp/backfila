package app.cash.backfila.client.s3.internal

import app.cash.backfila.client.s3.ForS3BackendV2
import app.cash.backfila.client.s3.shim.S3Service
import javax.inject.Inject
import javax.inject.Singleton
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.buffer
import okio.source
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request

@Singleton
class RealS3ServiceV2 @Inject constructor(
  @ForS3BackendV2 private val client: S3Client,
) : S3Service {

  override fun listFiles(bucket: String, keyPrefix: String): List<String> {
    val request = ListObjectsV2Request.builder()
      .bucket(bucket)
      .prefix(keyPrefix)
      .build()
    // While S3 pagination is possible, we intentionally do not handle it here to match
    // the V1 behavior in RealS3Service. S3DatasourceBackfillOperator strictly limits
    // processing to <= 100 files, so a single page result is sufficient for this constraint.

    val response = client.listObjectsV2(request)
    return response.contents().map { it.key() }
  }

  override fun getFileSize(bucket: String, key: String): Long {
    val request = HeadObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .build()
    return client.headObject(request).contentLength()
  }

  override fun getFileStreamStartingAt(bucket: String, key: String, start: Long): BufferedSource {
    val request = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .range("bytes=$start-")
      .build()
    return client.getObject(request).source().buffer()
  }

  override fun getWithSeek(bucket: String, key: String, seekStart: Long, seekEnd: Long): ByteString {
    val request = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .range("bytes=$seekStart-$seekEnd")
      .build()

    return Buffer().run {
      client.getObject(request).use { responseStream ->
        writeAll(responseStream.source())
        readByteString()
      }
    }
  }
}
