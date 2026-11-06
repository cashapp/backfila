package app.cash.backfila.mcp

import app.cash.backfila.mcp.tools.ListServicesTool
import com.google.inject.Provider
import misk.inject.KAbstractModule
import misk.mcp.McpServerModule
import misk.mcp.McpToolModule
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.web.WebActionModule

/**
 * Exposes read-only Backfila tools to AI agents over the Model Context Protocol at `POST /mcp`.
 *
 * Install this alongside Backfila's service modules. Callers authenticate with the host's
 * authenticator, the same way they do for the dashboard.
 */
@ExperimentalBackfilaApi
class BackfilaMcpModule @JvmOverloads constructor(
  private val serverConfig: McpServerConfig = McpServerConfig(version = SERVER_VERSION),
) : KAbstractModule() {
  override fun configure() {
    val instructions = getProvider(BackfilaMcpInstructions::class.java)
    install(
      McpServerModule.create(
        name = SERVER_NAME,
        config = McpConfig(mapOf(SERVER_NAME to serverConfig)),
        instructionsProvider = Provider { instructions.get().text },
      ),
    )
    install(WebActionModule.create<BackfilaMcpWebAction>())

    install(McpToolModule.create<ListServicesTool>())
  }

  companion object {
    const val SERVER_NAME = "backfila"
    const val SERVER_VERSION = "0.1.0-experimental"
  }
}
