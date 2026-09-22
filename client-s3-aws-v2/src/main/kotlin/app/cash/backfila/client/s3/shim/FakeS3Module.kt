package app.cash.backfila.client.s3.shim

import com.google.inject.AbstractModule

class FakeS3Module : AbstractModule() {
  override fun configure() {
    bind(S3Service::class.java).to(FakeS3Service::class.java)
  }
}
