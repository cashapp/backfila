package app.cash.backfila.mcp

import jakarta.inject.Inject
import kotlinx.serialization.Serializable
import misk.MiskCaller
import misk.mcp.StructuredMcpToolEmptyInput
import misk.scope.ActionScoped

@Serializable
internal data class WhoAmIOutput(
  val user: String?,
)

/** Test-only tool that proves the request's [MiskCaller] is visible inside tool handlers. */
internal class WhoAmITool @Inject constructor(
  private val caller: ActionScoped<MiskCaller?>,
) : StructuredMcpToolEmptyInput<WhoAmIOutput>() {
  override val name = "who_am_i"
  override val description = "Returns the authenticated caller."
  override val readOnlyHint = true

  override suspend fun handle(): ToolResult = ToolResult(WhoAmIOutput(caller.get()?.user))
}
