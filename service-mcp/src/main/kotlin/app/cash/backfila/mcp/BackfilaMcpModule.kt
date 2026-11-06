package app.cash.backfila.mcp

import app.cash.backfila.mcp.tools.ListRegisteredBackfillsTool
import app.cash.backfila.mcp.tools.ListServicesTool
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfilaMcpConfig
import com.google.inject.Provider
import kotlin.reflect.KClass
import misk.inject.KAbstractModule
import misk.mcp.McpServerModule
import misk.mcp.McpTool
import misk.mcp.McpToolModule
import misk.mcp.config.McpConfig
import misk.mcp.config.McpServerConfig
import misk.web.WebActionModule

/**
 * Exposes Backfila tools to AI agents over the Model Context Protocol at `POST /mcp`, configured
 * by [BackfilaConfig.mcp]. Install it alongside `BackfilaServiceModule` with the same config. It
 * binds nothing unless `mcp.enabled` is true.
 *
 * The endpoint requires an authenticated user, like the dashboard's read endpoints. As with the
 * dashboard, that is only enforced if the host installs Misk's `AccessControlModule`; without it
 * `/mcp` is open to any caller.
 */
@ExperimentalBackfilaMcpApi
class BackfilaMcpModule(
  private val config: BackfilaConfig,
) : KAbstractModule() {
  override fun configure() {
    val mcpConfig = config.mcp
    if (!mcpConfig.enabled) return

    val instructions = getProvider(BackfilaMcpInstructions::class.java)
    install(
      McpServerModule.create(
        name = mcpConfig.server_name,
        config = McpConfig(mapOf(mcpConfig.server_name to McpServerConfig(version = SERVER_VERSION))),
        instructionsProvider = Provider { instructions.get().text },
        groupAnnotation = BackfilaMcp::class,
      ),
    )
    install(WebActionModule.create<BackfilaMcpWebAction>())

    backfilaMcpTools
      .boundFor(mcpConfig)
      .forEach { install(McpToolModule.create(it.toolClass, groupAnnotationClass = BackfilaMcp::class)) }
  }
}

internal class BackfilaMcpToolBinding(
  val toolClass: KClass<out McpTool<*>>,
  /** Must match the tool's `readOnlyHint`; read-only servers only bind tools where this is true. */
  val readOnly: Boolean,
)

internal val backfilaMcpTools: List<BackfilaMcpToolBinding> = listOf(
  BackfilaMcpToolBinding(ListServicesTool::class, readOnly = true),
  BackfilaMcpToolBinding(ListRegisteredBackfillsTool::class, readOnly = true),
)

internal fun List<BackfilaMcpToolBinding>.boundFor(config: BackfilaMcpConfig) =
  filter { it.readOnly || !config.read_only }

// Reported to clients in the MCP server info. Bump when tool names or schemas change.
private const val SERVER_VERSION = "0.1.0-experimental"
