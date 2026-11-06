package app.cash.backfila.service

import misk.config.Config

/** Configures the MCP server in `service-mcp`. Has no effect unless the host installs `BackfilaMcpModule`. */
data class BackfilaMcpConfig(
  /** Serves MCP only when true, so installing the module or adding this config doesn't turn it on. */
  val enabled: Boolean = false,
  /** Name this MCP server reports to clients. */
  val server_name: String = "backfila",
  /** When true, only tools that don't change Backfila state are exposed. */
  val read_only: Boolean = true,
) : Config
