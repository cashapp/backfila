package app.cash.backfila.mcp

/**
 * Marks Backfila APIs built on `misk-mcp`, which is itself `@ExperimentalMiskApi`.
 *
 * These APIs, and the MCP tool names and schemas they expose, may change incompatibly between
 * releases. They stay experimental until `misk-mcp` is stable.
 */
@RequiresOptIn(
  message = "Backfila's MCP support is experimental and may change incompatibly between releases.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.CONSTRUCTOR,
  AnnotationTarget.TYPEALIAS,
)
annotation class ExperimentalBackfilaApi
