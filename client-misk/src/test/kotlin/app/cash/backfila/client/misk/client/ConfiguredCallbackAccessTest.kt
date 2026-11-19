package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaHttpClientConfig
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebTestClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ConfiguredCallbackAccessTest {
  @Suppress("unused")
  @MiskTestModule
  val module = CallbackAccessTestingModule(
    BackfilaHttpClientConfig(
      url = "http://localhost",
      slack_channel = null,
      callback_services = listOf("regional-backfila"),
    ),
  )

  @Inject lateinit var webTestClient: WebTestClient

  @Test
  fun `a configured service may call the callbacks`() {
    assertThat(webTestClient.prepareBackfillAs("regional-backfila")).isEqualTo(400)
  }

  @Test
  fun `the configured list replaces the default`() {
    assertThat(webTestClient.prepareBackfillAs("backfila")).isEqualTo(403)
  }
}
