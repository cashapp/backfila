package app.cash.backfila.client.s3.shim

import app.cash.backfila.client.s3.ForS3Backend
import com.google.inject.AbstractModule

class FakeS3Module : AbstractModule() {
  override fun configure() {
    bind(S3Service::class.java).annotatedWith(ForS3Backend::class.java).to(FakeS3Service::class.java)
  }
}
