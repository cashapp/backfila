package app.cash.backfila.mcp

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import misk.exceptions.BadRequestException
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.handleMessage
import misk.mcp.decode
import misk.security.authz.Authenticated
import misk.web.RequestBody
import misk.web.actions.WebAction
import misk.web.sse.ServerSentEvent

@Singleton
internal class BackfilaMcpWebAction @Inject constructor(
  private val mcpStreamManager: McpStreamManager,
) : WebAction {
  // Same access as the dashboard's read endpoints: any authenticated user.
  @McpPost
  @Authenticated(allowAnyUser = true)
  suspend fun mcp(
    // A String, not JSONRPCMessage: misk's Moshi unmarshaller claims any other type for
    // application/json whenever MiskWebModule is installed before this module.
    @RequestBody body: String,
    sendChannel: SendChannel<ServerSentEvent>,
  ) {
    val message = try {
      body.decode<JSONRPCMessage>()
    } catch (e: IllegalArgumentException) {
      throw BadRequestException("Invalid MCP JSON-RPC message", e)
    }
    mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
  }
}
