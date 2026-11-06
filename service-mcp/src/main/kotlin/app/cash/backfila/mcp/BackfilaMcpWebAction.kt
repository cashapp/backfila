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
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.HttpCall
import misk.web.actions.WebAction
import misk.web.sse.ServerSentEvent

@Singleton
internal class BackfilaMcpWebAction @Inject constructor(
  private val httpCall: ActionScoped<HttpCall>,
  private val mcpStreamManager: McpStreamManager,
) : WebAction {
  // Same access as the dashboard's read endpoints: any authenticated user.
  @McpPost
  @Authenticated(allowAnyUser = true)
  suspend fun mcp(sendChannel: SendChannel<ServerSentEvent>) {
    // Decoded here rather than with @RequestBody: misk picks the first unmarshaller that accepts
    // application/json, so the MCP one only wins if this module is installed before MiskWebModule.
    val body = httpCall.get().takeRequestBody() ?: throw BadRequestException("Missing MCP JSON-RPC message")
    val message = try {
      body.readUtf8().decode<JSONRPCMessage>()
    } catch (e: IllegalArgumentException) {
      // Includes kotlinx.serialization's SerializationException.
      throw BadRequestException("Invalid MCP JSON-RPC message", e)
    }
    mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
  }
}
