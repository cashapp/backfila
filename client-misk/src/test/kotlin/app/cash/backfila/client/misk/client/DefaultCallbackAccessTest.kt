package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaHttpClientConfig
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebTestClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class DefaultCallbackAccessTest {
  @Suppress("unused")
  @MiskTestModule
  val module = CallbackAccessTestingModule(
    BackfilaHttpClientConfig(url = "http://localhost", slack_channel = null),
  )

  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun `backfila may call the callbacks`() {
    assertThat(webTestClient.prepareBackfillAs("backfila")).isEqualTo(400)
  }

  @Test
  fun `another service may not call the callbacks`() {
    assertThat(webTestClient.prepareBackfillAs("regional-backfila")).isEqualTo(403)
  }
}
