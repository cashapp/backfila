package app.cash.backfila.client.s3.shim

import app.cash.backfila.client.s3.ForS3Backend
import app.cash.backfila.client.s3.internal.RealS3ServiceV2
import com.google.inject.AbstractModule
import com.google.inject.Key
import software.amazon.awssdk.services.s3.S3Client

class RealS3V2Module : AbstractModule() {
  override fun configure() {
    requireBinding(Key.get(S3Client::class.java).withAnnotation(ForS3Backend::class.java))
    bind(S3Service::class.java).to(RealS3ServiceV2::class.java)
  }
}
