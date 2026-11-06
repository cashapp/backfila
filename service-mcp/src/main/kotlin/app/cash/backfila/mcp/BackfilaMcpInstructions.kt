package app.cash.backfila.mcp

import app.cash.backfila.service.BackfilaConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Server instructions sent to MCP clients when they connect. Keep guidance that spans tools here;
 * guidance for a single tool belongs in that tool's description.
 */
@Singleton
internal class BackfilaMcpInstructions @Inject constructor(
  private val config: BackfilaConfig,
) {
  val text: String by lazy {
    val dashboardRoot = config.web_url_root.trimEnd('/')
    val dashboard = if (dashboardRoot.isBlank()) "" else "\n- The Backfila dashboard is at $dashboardRoot/."
    """
    |Backfila runs backfills: batched jobs that a service registers with Backfila, which Backfila then drives by calling back into that service.
    |
    |- A service is identified by its name and a variant. Services with a single deployment use the variant "default".
    |- Call list_services first to find valid service names and variants.
    |- These tools are read-only. To create, start, pause, or cancel a backfill, send the user to the Backfila dashboard.
    """.trimMargin() + dashboard
  }
}
