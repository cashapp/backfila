package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.mcp.mcpClient
import app.cash.backfila.mcp.registerService
import app.cash.backfila.service.persistence.BackfilaDb
import com.google.inject.Module
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class ListServicesToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule()

  @Inject lateinit var jettyService: JettyService

  @Inject @BackfilaDb
  lateinit var transacter: Transacter

  @Test
  fun `list_services is advertised as a read-only tool with a structured output schema`(): Unit = runBlocking {
    val tool = jettyService.mcpClient().listTools().tools.single { it.name == "list_services" }

    assertThat(tool.annotations?.readOnlyHint).isTrue()
    val outputSchema = tool.outputSchema!!
    assertThat(outputSchema.required).containsExactly("services")
    val service = outputSchema.properties!!["services"]!!.jsonObject["items"]!!.jsonObject
    assertThat(service["required"]!!.jsonArray.map { it.jsonPrimitive.content })
      .containsExactlyInAnyOrder("name", "variants", "running_backfills")
  }

  @Test
  fun `list_services returns registered services and their variants`(): Unit = runBlocking {
    transacter.registerService("deep-fryer", "default")
    transacter.registerService("deep-fryer", "staging")
    transacter.registerService("grill")

    val result = jettyService.mcpClient().callTool(name = "list_services", arguments = mapOf())

    assertThat(result.isError).isNotEqualTo(true)
    val output = Json.decodeFromJsonElement(ListServicesOutput.serializer(), result.structuredContent!!)
    assertThat(output.services).containsExactly(
      ListServicesOutput.Service(name = "deep-fryer", variants = listOf("default", "staging"), running_backfills = 0),
      ListServicesOutput.Service(name = "grill", variants = listOf("default"), running_backfills = 0),
    )
  }
}
