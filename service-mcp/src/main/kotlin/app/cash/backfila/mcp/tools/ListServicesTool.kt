package app.cash.backfila.mcp.tools

import app.cash.backfila.dashboard.GetServicesAction
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable
import misk.mcp.Description
import misk.mcp.StructuredMcpToolEmptyInput

@Serializable
internal data class ListServicesOutput(
  val services: List<Service>,
) {
  @Serializable
  data class Service(
    @Description("Service name, as registered with Backfila.")
    val name: String,
    @Description("Variants registered for this service. \"default\" is used when a service has one deployment.")
    val variants: List<String>,
    @Description("Number of backfills currently running across all of this service's variants.")
    val running_backfills: Int,
  )
}

@Singleton
internal class ListServicesTool @Inject constructor(
  private val getServicesAction: GetServicesAction,
) : StructuredMcpToolEmptyInput<ListServicesOutput>() {
  override val name = "list_services"
  override val title = "List services"
  override val description =
    "Lists every service registered with Backfila, with its variants and how many backfills are running."
  override val readOnlyHint = true
  override val openWorldHint = false

  override suspend fun handle(): ToolResult {
    val services = getServicesAction.services().services.map { service ->
      ListServicesOutput.Service(
        name = service.name,
        variants = service.variants.sorted(),
        running_backfills = service.running_backfills,
      )
    }
    return ToolResult(ListServicesOutput(services))
  }
}
