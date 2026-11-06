package app.cash.backfila.mcp

import misk.config.Config

@ExperimentalBackfilaMcpApi
data class BackfilaMcpConfig @JvmOverloads constructor(
  /** Name this MCP server reports to clients. */
  val server_name: String = "backfila",
  /** When true, only tools that don't change Backfila state are exposed. */
  val read_only: Boolean = true,
) : Config
