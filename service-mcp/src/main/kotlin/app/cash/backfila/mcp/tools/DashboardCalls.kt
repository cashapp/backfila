package app.cash.backfila.mcp.tools

internal const val UNKNOWN_SERVICE_HINT = "Call list_services for valid service names and variants."

/**
 * Validates a pagination token from an earlier response. The dashboard's paginators use row IDs as
 * tokens and fail on anything else. Treats a blank token as absent, since agents often send empty
 * strings for optional fields.
 */
internal fun paginationToken(name: String, token: String?): String? {
  if (token.isNullOrBlank()) return null
  if (token.toLongOrNull() == null) {
    throw ToolError("$name must be a token returned by a previous call. Leave it out to get the first page.")
  }
  return token
}
