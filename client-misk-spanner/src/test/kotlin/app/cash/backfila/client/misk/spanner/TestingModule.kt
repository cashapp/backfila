package app.cash.backfila.client.misk.spanner

import app.cash.backfila.embedded.EmbeddedBackfilaModule
import misk.MiskTestingServiceModule
import misk.cloud.gcp.spanner.GoogleSpannerEmulatorModule
import misk.cloud.gcp.spanner.GoogleSpannerModule
import misk.cloud.gcp.spanner.SpannerConfig
import misk.cloud.gcp.spanner.SpannerEmulatorConfig
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import wisp.deployment.TESTING

/**
 * Simulates a specific service implementation module
 */
class TestingModule : KAbstractModule() {
  companion object {
    val DB_ID = "test-db"
    val INSTANCE_ID = "test"
    val PROJECT_ID = "test"
  }

  override fun configure() {
    install(DeploymentModule(TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(BackfillsModule())

    install(EmbeddedBackfilaModule())

    val spannerConfig = SpannerConfig(
      database = DB_ID,
      emulator = SpannerEmulatorConfig(
        enabled = true,
      ),
      instance_id = INSTANCE_ID,
      project_id = PROJECT_ID,
    )
    install(GoogleSpannerModule(spannerConfig))
    install(GoogleSpannerEmulatorModule(spannerConfig))
  }
}
