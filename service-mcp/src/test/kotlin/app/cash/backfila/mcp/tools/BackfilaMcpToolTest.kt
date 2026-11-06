package app.cash.backfila.mcp.tools

import app.cash.backfila.mcp.BackfilaMcp
import app.cash.backfila.mcp.BackfilaMcpTestingModule
import app.cash.backfila.mcp.mcpClient
import com.google.inject.Module
import com.google.inject.util.Modules
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import misk.inject.KAbstractModule
import misk.mcp.McpToolModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfilaMcpToolTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = Modules.combine(
    BackfilaMcpTestingModule(),
    object : KAbstractModule() {
      override fun configure() {
        install(McpToolModule.create<BackfilaMcp, RejectingTool>())
      }
    },
  )

  @Inject lateinit var jettyService: JettyService

  @Test
  fun `tool errors are returned to the agent as error results`(): Unit = runBlocking {
    val result = jettyService.mcpClient().callTool(name = "reject", arguments = mapOf("reason" to "no fries"))

    assertThat(result.isError).isTrue()
    assertThat((result.content.single() as TextContent).text).isEqualTo("no fries")
  }
}

@Serializable
internal data class RejectInput(val reason: String)

@Serializable
internal data class RejectOutput(val unused: String)

internal class RejectingTool @Inject constructor() : BackfilaMcpTool<RejectInput, RejectOutput>() {
  override val name = "reject"
  override val description = "Always fails with the given reason."

  override suspend fun call(input: RejectInput): RejectOutput = throw ToolError(input.reason)
}
