package app.cash.backfila.service

import java.io.File
import java.nio.file.Path
import misk.config.MiskConfig
import misk.jdbc.DataSourceClustersConfig
import misk.slack.SlackConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import wisp.deployment.Deployment

class BackfilaConfigTest {
  @TempDir lateinit var dir: Path

  @Test
  fun `loads from yaml without an mcp section`() {
    val config = load(
      """
      backfill_runner_threads: 3
      data_source_clusters: {}
      web_url_root: "https://backfila.example.com/"
      """,
    )

    assertThat(config.backfill_runner_threads).isEqualTo(3)
    assertThat(config.mcp).isEqualTo(BackfilaMcpConfig())
  }

  @Test
  fun `loads the mcp section from yaml`() {
    val config = load(
      """
      data_source_clusters: {}
      web_url_root: "https://backfila.example.com/"
      mcp:
        enabled: true
        read_only: false
      """,
    )

    assertThat(config.mcp).isEqualTo(BackfilaMcpConfig(enabled = true, read_only = false))
  }

  /** Callers compiled before `mcp` was added link against these signatures. */
  @Test
  fun `keeps the constructor and copy from before mcp was added`() {
    val oldParameters = arrayOf(
      Integer::class.java,
      DataSourceClustersConfig::class.java,
      SlackConfig::class.java,
      String::class.java,
      Int::class.javaPrimitiveType,
      Int::class.javaPrimitiveType,
      String::class.java,
      String::class.java,
    )
    val oldArguments = arrayOf(3, DataSourceClustersConfig(), null, "https://backfila.example.com/", 1, 2, null, null)

    val constructed = BackfilaConfig::class.java.getConstructor(*oldParameters).newInstance(*oldArguments)
    assertThat(constructed.mcp).isEqualTo(BackfilaMcpConfig())

    val enabled = constructed.copy(mcp = BackfilaMcpConfig(enabled = true))
    val copied = BackfilaConfig::class.java.getMethod("copy", *oldParameters)
      .invoke(enabled, 4, DataSourceClustersConfig(), null, "https://other.example.com/", 1, 2, null, null) as BackfilaConfig
    assertThat(copied.backfill_runner_threads).isEqualTo(4)
    assertThat(copied.mcp).isEqualTo(BackfilaMcpConfig(enabled = true))
  }

  private fun load(yaml: String): BackfilaConfig {
    val file = File(dir.toFile(), "backfila.yaml").apply { writeText(yaml.trimIndent()) }
    return MiskConfig.load(BackfilaConfig::class.java, "backfila-config-test", Deployment(name = "test", isTest = true), listOf(file))
  }
}
