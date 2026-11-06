package app.cash.backfila.mcp.tools

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import app.cash.backfila.service.persistence.ServiceQuery
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.serialization.Serializable
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.mcp.Description

@Serializable
internal data class ListRegisteredBackfillsInput(
  @Description("Service name, from list_services.")
  val service: String,
  @Description("Service variant, from list_services. Defaults to \"default\".")
  val variant: String = "default",
)

@Serializable
internal data class ListRegisteredBackfillsOutput(
  val backfills: List<RegisteredBackfill>,
) {
  @Serializable
  data class RegisteredBackfill(
    @Description("Backfill name, as registered by the service.")
    val name: String,
    @Description("Parameters this backfill accepts.")
    val parameters: List<Parameter>,
  )

  @Serializable
  data class Parameter(
    val name: String,
    @Description("What the parameter does, if the service describes it.")
    val description: String?,
    @Description("True if the backfill can't run without it.")
    val required: Boolean,
  )
}

@Singleton
internal class ListRegisteredBackfillsTool @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : BackfilaMcpTool<ListRegisteredBackfillsInput, ListRegisteredBackfillsOutput>() {
  override val name = "list_registered_backfills"
  override val title = "List registered backfills"
  override val description =
    "Lists the backfills a service variant has registered with Backfila, which are the backfills that can be run for it, with their parameters."
  override val readOnlyHint = true
  override val openWorldHint = false

  // Reads the database directly because the dashboard's GetRegisteredBackfillsAction only returns
  // parameter names, not their descriptions.
  override suspend fun call(input: ListRegisteredBackfillsInput): ListRegisteredBackfillsOutput =
    transacter.transaction { session ->
      val service = queryFactory.newQuery<ServiceQuery>()
        .registryName(input.service)
        .variant(input.variant)
        .uniqueResult(session)
        ?: throw ToolError("`${input.service}`-`${input.variant}` doesn't exist. $UNKNOWN_SERVICE_HINT")
      val backfills = queryFactory.newQuery<RegisteredBackfillQuery>()
        .serviceId(service.id)
        .active()
        .orderByName()
        .list(session)
      ListRegisteredBackfillsOutput(backfills.map { it.toOutput() })
    }

  private fun DbRegisteredBackfill.toOutput() = ListRegisteredBackfillsOutput.RegisteredBackfill(
    name = name,
    parameters = if (parameters.isNotEmpty()) {
      parameters.map { ListRegisteredBackfillsOutput.Parameter(it.name, it.description, it.required) }
    } else {
      // Registrations from before parameters were stored with descriptions only have names.
      parameterNames().map { ListRegisteredBackfillsOutput.Parameter(it, description = null, required = false) }
    },
  )
}
