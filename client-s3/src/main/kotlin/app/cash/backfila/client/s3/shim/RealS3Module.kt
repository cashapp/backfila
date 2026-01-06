package app.cash.backfila.client.s3.shim

import app.cash.backfila.client.s3.ForS3Backend
import app.cash.backfila.client.s3.internal.RealS3Service
import com.amazonaws.services.s3.AmazonS3
import com.google.inject.AbstractModule
import com.google.inject.Key

@Deprecated(
  message = "AWS V1 SDK is deprecated, use RealS3AwsV2Module instead.",
  replaceWith = ReplaceWith(
    expression = "RealS3AwsV2Module",
    imports =  ["app.cash.backfila.client.s3.shim.RealS3AwsV2Module"],
  ),
  level = DeprecationLevel.WARNING,
)
class RealS3Module : AbstractModule() {
  override fun configure() {
    requireBinding(Key.get(AmazonS3::class.java).withAnnotation(ForS3Backend::class.java))
    bind(S3Service::class.java).to(RealS3Service::class.java)
  }
}
