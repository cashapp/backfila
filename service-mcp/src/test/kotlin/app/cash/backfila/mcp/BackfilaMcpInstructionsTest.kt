package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfilaMcpConfig
import misk.jdbc.DataSourceClustersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BackfilaMcpInstructionsTest {
  @Test
  fun `read-only servers tell agents to use the dashboard for changes`() {
    val text = instructions(read_only = true)

    assertThat(text).contains("These tools are read-only.")
  }

  @Test
  fun `servers with mutating tools don't claim to be read-only`() {
    val text = instructions(read_only = false)

    assertThat(text).doesNotContain("read-only")
  }

  @Test
  fun `agents are told to start with list_services`() {
    assertThat(instructions()).contains("Call list_services first")
  }

  @Test
  fun `dashboard link is omitted when no web url root is configured`() {
    assertThat(instructions(webUrlRoot = "https://backfila.example.com")).contains("https://backfila.example.com/.")
    assertThat(instructions(webUrlRoot = "")).doesNotContain("dashboard is at")
  }

  private fun instructions(read_only: Boolean = true, webUrlRoot: String = ""): String {
    val config = BackfilaConfig(
      backfill_runner_threads = null,
      data_source_clusters = DataSourceClustersConfig(mapOf()),
      slack = null,
      web_url_root = webUrlRoot,
      mcp = BackfilaMcpConfig(read_only = read_only),
    )
    return BackfilaMcpInstructions(config, DashboardLinks(config)).text
  }
}
