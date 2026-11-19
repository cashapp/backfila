package app.cash.backfila.client.misk.client

import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import misk.security.authz.FakeCallerAuthenticator
import misk.web.WebTestClient
import misk.web.mediatype.MediaTypes
import okhttp3.RequestBody.Companion.toRequestBody

fun WebTestClient.prepareBackfillAs(service: String): Int {
  val body = PrepareBackfillRequest.Builder().backfill_name("unknown").build().encode()
  return call("/backfila/prepare-backfill") {
    header(FakeCallerAuthenticator.SERVICE_HEADER, service)
    header("Content-Type", MediaTypes.APPLICATION_PROTOBUF)
    post(body.toRequestBody(MediaTypes.APPLICATION_PROTOBUF_MEDIA_TYPE))
  }.response.use { it.code }
}
