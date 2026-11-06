package app.cash.backfila.service

import misk.config.Config

/** Configures the MCP server in `service-mcp`. Has no effect unless the host installs `BackfilaMcpModule`. */
data class BackfilaMcpConfig(
  /** Name this MCP server reports to clients. */
  val server_name: String = "backfila",
  /** When true, only tools that don't change Backfila state are exposed. */
  val read_only: Boolean = true,
) : Config
