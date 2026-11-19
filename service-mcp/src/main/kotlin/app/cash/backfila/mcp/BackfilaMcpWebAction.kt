package app.cash.backfila.mcp

import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.channels.SendChannel
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthenticatedException
import misk.mcp.action.McpPost
import misk.mcp.action.McpStreamManager
import misk.mcp.action.handleMessage
import misk.mcp.decode
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.RequestBody
import misk.web.actions.WebAction
import misk.web.sse.ServerSentEvent

@Singleton
internal class BackfilaMcpWebAction @Inject constructor(
  @BackfilaMcp private val mcpStreamManager: McpStreamManager,
  private val caller: ActionScoped<MiskCaller?>,
) : WebAction {
  @McpPost
  @Authenticated(allowAnyUser = true)
  suspend fun mcp(
    @RequestBody body: String,
    sendChannel: SendChannel<ServerSentEvent>,
  ) {
    // @Authenticated is only enforced when the host installs AccessControlModule; reject anonymous
    // callers here too so a host that forgets it doesn't expose Backfila.
    if (caller.get() == null) throw UnauthenticatedException()
    val message = try {
      body.decode<JSONRPCMessage>()
    } catch (e: IllegalArgumentException) {
      throw BadRequestException("Invalid MCP JSON-RPC message", e)
    }
    mcpStreamManager.withSseChannel(sendChannel) { handleMessage(message) }
  }
}
