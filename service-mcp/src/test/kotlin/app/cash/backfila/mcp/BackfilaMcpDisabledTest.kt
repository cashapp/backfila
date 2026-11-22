package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaMcpConfig
import com.google.inject.Module
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
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
    jettyService.postMcp("{}", user = "molly").use { response ->
      assertThat(response.code).isEqualTo(404)
    }
  }
}
