package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaMcpConfig
import com.google.inject.Module
import jakarta.inject.Inject
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfilaMcpDisabledTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule(mcp = BackfilaMcpConfig())

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `mcp isn't served unless enabled`() {
    val request = Request.Builder()
      .url(jettyService.httpServerUrl.resolve("/mcp")!!)
      .header(FakeCallerAuthenticator.USER_HEADER, "molly")
      .header("Accept", "application/json, text/event-stream")
      .post("{}".toRequestBody("application/json".toMediaType()))
      .build()

    OkHttpClient().newCall(request).execute().use { response ->
      assertThat(response.code).isEqualTo(404)
    }
  }
}
