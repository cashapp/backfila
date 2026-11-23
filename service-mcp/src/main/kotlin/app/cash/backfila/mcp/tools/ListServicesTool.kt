package app.cash.backfila.mcp.tools

import app.cash.backfila.dashboard.GetServicesAction
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable
import misk.mcp.Description

@Serializable
internal data class ListServicesInput(
  @Description("Only include services whose name contains this text, ignoring case.")
  val name: String? = null,
)

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
) : BackfilaMcpTool<ListServicesInput, ListServicesOutput>() {
  override val name = "list_services"
  override val title = "List services"
  override val description =
    "Lists the services registered with Backfila, with their variants and how many backfills are running in each. " +
      "Use it to find valid service names and variants for the other tools."
  override val readOnlyHint = true
  override val openWorldHint = false

  override suspend fun call(input: ListServicesInput): ListServicesOutput {
    val name = input.name?.ifBlank { null }
    val services = getServicesAction.services().services
      .filter { name == null || it.name.contains(name, ignoreCase = true) }
      .map { service ->
        ListServicesOutput.Service(
          name = service.name,
          variants = service.variants.sorted(),
          running_backfills = service.running_backfills,
        )
      }
    return ListServicesOutput(services)
  }
}
