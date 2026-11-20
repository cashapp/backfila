package app.cash.backfila.mcp

import jakarta.inject.Qualifier

/**
 * Qualifies the bindings misk-mcp creates for Backfila's server: its `Set<McpTool<*>>` multibinder,
 * `McpStreamManager`, and `MiskMcpServer`. Without it those are unqualified, so any tool a host
 * registers with `McpToolModule.create<T>()` would be served by Backfila, and a host's own
 * `McpServerModule` would fail with duplicate bindings.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class BackfilaMcp
