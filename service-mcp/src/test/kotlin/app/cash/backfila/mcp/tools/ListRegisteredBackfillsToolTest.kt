package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.mcp.callStructured
import app.cash.backfila.mcp.mcpClient
import app.cash.backfila.mcp.registerBackfill
import app.cash.backfila.mcp.registerService
import app.cash.backfila.protos.service.Parameter
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import com.google.inject.Module
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ListRegisteredBackfillsToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `only service is required`(): Unit = runBlocking {
    val tool = jettyService.mcpClient().listTools().tools.single { it.name == "list_registered_backfills" }

    assertThat(tool.annotations?.readOnlyHint).isTrue()
    assertThat(tool.inputSchema.required).containsExactly("service")
  }

  @Test
  fun `lists the variant's backfills by name, with parameter descriptions`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    transacter.registerBackfill(
      fryer,
      "RefillOil",
      parameters = listOf(Parameter("brand", "Oil brand to buy.", true), Parameter("litres", null, false)),
    )
    transacter.registerBackfill(fryer, "DrainOil")
    val stagingFryer = transacter.registerService("deep-fryer", "staging")
    transacter.registerBackfill(stagingFryer, "StagingOnly")

    val output = listRegisteredBackfills(mapOf("service" to "deep-fryer"))

    assertThat(output.backfills).containsExactly(
      ListRegisteredBackfillsOutput.RegisteredBackfill(name = "DrainOil", parameters = listOf()),
      ListRegisteredBackfillsOutput.RegisteredBackfill(
        name = "RefillOil",
        parameters = listOf(
          ListRegisteredBackfillsOutput.Parameter("brand", "Oil brand to buy.", required = true),
          ListRegisteredBackfillsOutput.Parameter("litres", description = null, required = false),
        ),
      ),
    )
  }

  @Test
  fun `falls back to parameter names for backfills registered without parameter rows`(): Unit = runBlocking {
    val fryer = transacter.registerService("deep-fryer")
    val legacy = transacter.registerBackfill(fryer, "RefillOil")
    transacter.transaction { session -> session.load<DbRegisteredBackfill>(legacy).parameter_names = "brand,litres" }

    val output = listRegisteredBackfills(mapOf("service" to "deep-fryer"))

    assertThat(output.backfills.single().parameters).containsExactly(
      ListRegisteredBackfillsOutput.Parameter("brand", description = null, required = false),
      ListRegisteredBackfillsOutput.Parameter("litres", description = null, required = false),
    )
  }

  @Test
  fun `unknown services are tool errors that point at list_services`(): Unit = runBlocking {
    val result = jettyService.mcpClient().callTool(
      name = "list_registered_backfills",
      arguments = mapOf("service" to "deep-fryer", "variant" to "nope"),
    )

    assertThat(result.isError).isTrue()
    assertThat((result.content.single() as TextContent).text)
      .contains("`deep-fryer`-`nope` doesn't exist", "list_services")
  }

  private suspend fun listRegisteredBackfills(arguments: Map<String, Any?>): ListRegisteredBackfillsOutput =
    jettyService.mcpClient().callStructured("list_registered_backfills", arguments)
}
