package app.cash.backfila.mcp

import jakarta.inject.Qualifier

/** Groups Backfila's MCP server and tools so they don't mix with a host's own MCP servers or tools. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
internal annotation class BackfilaMcp
