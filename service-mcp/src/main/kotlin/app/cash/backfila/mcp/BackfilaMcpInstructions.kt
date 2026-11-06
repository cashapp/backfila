package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton

/** Server instructions sent to MCP clients when they connect. */
@Singleton
internal class BackfilaMcpInstructions @Inject constructor(
  private val config: BackfilaConfig,
) {
  val text: String by lazy {
    buildString {
      appendLine(
        "Backfila runs backfills: batched jobs that a service registers with Backfila, which Backfila then " +
          "drives by calling back into that service.",
      )
      appendLine()
      appendLine(
        "- A service is identified by its name and a variant. Services with a single deployment use the " +
          "variant \"default\".",
      )
      appendLine("- Call list_services first to find valid service names and variants.")
      appendLine(
        "- These tools are read-only. To create, start, pause, or cancel a backfill, send the user to the " +
          "Backfila dashboard.",
      )
      val dashboardRoot = config.web_url_root.trimEnd('/')
      if (dashboardRoot.isNotBlank()) {
        appendLine("- The Backfila dashboard is at $dashboardRoot/.")
      }
    }.trimEnd()
  }
}
