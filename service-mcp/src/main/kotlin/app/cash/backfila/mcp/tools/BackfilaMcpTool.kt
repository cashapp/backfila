package app.cash.backfila.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import misk.mcp.StructuredMcpTool

/** Base for Backfila's MCP tools. Implement [call], and throw [ToolError] for problems the agent can fix. */
internal abstract class BackfilaMcpTool<I : Any, O : Any> : StructuredMcpTool<I, O>() {
  abstract suspend fun call(input: I): O

  final override suspend fun handle(input: I): ToolResult = try {
    ToolResult(call(input))
  } catch (e: ToolError) {
    ToolResult(TextContent(e.message), isError = true)
  }
}

/**
 * A problem with the agent's request, returned to it as a tool error. Unlike other exceptions, it
 * isn't logged as a server error.
 */
internal class ToolError(override val message: String) : Exception(message)
