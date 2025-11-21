package app.cash.backfila.client.s3.internal

import app.cash.backfila.client.s3.ForS3Backend
import app.cash.backfila.client.s3.shim.S3Service
import javax.inject.Inject
import javax.inject.Singleton
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.buffer
import okio.source
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectAttributesRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsRequest

@Singleton
class RealS3AwsV2Service @Inject constructor(
  @ForS3Backend private val s3Client: S3Client,
) : S3Service {

  override fun listFiles(bucket: String, pathPrefix: String): List<String> {
    val request = ListObjectsRequest.builder()
      .bucket(bucket)
      .prefix(pathPrefix)
      .build()
    val s3ObjectListing = s3Client.listObjects(request)
    val summaries = s3ObjectListing.contents()
    return summaries.map { it.key() }
  }

  override fun getFileSize(bucket: String, key: String): Long {
    val request = GetObjectAttributesRequest.builder()
      .bucket(bucket)
      .key(key)
      .build()
    val objectMetadata = s3Client.getObjectAttributes(request)
    return objectMetadata.objectSize()
  }

  override fun getFileStreamStartingAt(bucket: String, key: String, start: Long): BufferedSource {
    val request = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .range("bytes=$start-")
      .build()
    val s3Object = s3Client.getObject(request)
    return s3Object.source().buffer()
  }

  override fun getWithSeek(bucket: String, key: String, seekStart: Long, seekEnd: Long): ByteString {
    val request = GetObjectRequest.builder()
      .bucket(bucket)
      .key(key)
      .range("bytes=$seekStart-$seekEnd")
      .build()
    val s3Object = s3Client.getObject(request)
    return Buffer().run {
      s3Object.use {
        writeAll(it.source())
        readByteString()
      }
    }
  }
}
