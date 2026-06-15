package app.cash.backfila.client

import jakarta.inject.Provider as JakartaProvider
import javax.inject.Provider as JavaxProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class RealBackfillModuleCompatibilityTest {
  private val config = BackfilaClientConfig(
    slack_channel = null,
    connector_type = Connectors.HTTP,
    connector_extra_data = "{}",
    variant = null,
  )

  @Test fun `supports javax provider with kotlin logging provider class`() {
    val configProvider = JavaxProvider { config }

    val module = RealBackfillModule(
      configProvider,
      BackfilaClientNoLoggingSetupProvider::class,
    )

    assertThat(module).isNotNull()
  }

  @Test fun `supports javax provider with java logging provider class`() {
    val configProvider = JavaxProvider { config }

    val module = RealBackfillModule(
      configProvider,
      BackfilaClientNoLoggingSetupProvider::class.java,
    )

    assertThat(module).isNotNull()
  }

  @Test fun `supports jakarta provider with kotlin logging provider class`() {
    val configProvider = JakartaProvider { config }

    val module = RealBackfillModule(
      configProvider,
      BackfilaClientNoLoggingSetupProvider::class,
    )

    assertThat(module).isNotNull()
  }

  @Test fun `supports jakarta provider with java logging provider class`() {
    val configProvider = JakartaProvider { config }

    val module = RealBackfillModule(
      configProvider,
      BackfilaClientNoLoggingSetupProvider::class.java,
    )

    assertThat(module).isNotNull()
  }
}
