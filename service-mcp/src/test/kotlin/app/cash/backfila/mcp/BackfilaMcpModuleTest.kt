package app.cash.backfila.mcp

import app.cash.backfila.mcp.tools.RejectingTool
import app.cash.backfila.service.BackfilaMcpConfig
import com.google.inject.Injector
import com.google.inject.Module
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest
class BackfilaMcpModuleTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaMcpTestingModule(BackfilaMcpConfig(enabled = true, read_only = false))

  @Inject lateinit var injector: Injector

  @Test
  fun `read-only servers only bind read-only tools`() {
    val bindings = listOf(
      BackfilaMcpToolBinding(WhoAmITool::class, readOnly = true),
      BackfilaMcpToolBinding(RejectingTool::class, readOnly = false),
    )

    assertThat(bindings.boundFor(BackfilaMcpConfig(read_only = true)).map { it.toolClass })
      .containsExactly(WhoAmITool::class)
    assertThat(bindings.boundFor(BackfilaMcpConfig(read_only = false)).map { it.toolClass })
      .containsExactly(WhoAmITool::class, RejectingTool::class)
  }

  @Test
  fun `each tool's binding matches its read-only hint`() {
    backfilaMcpTools.forEach { binding ->
      val tool = injector.getInstance(binding.toolClass.java)
      assertThat(tool.readOnlyHint).describedAs(tool.name).isEqualTo(binding.readOnly)
    }
  }
}
