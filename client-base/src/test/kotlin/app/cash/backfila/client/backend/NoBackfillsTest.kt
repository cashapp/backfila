package app.cash.backfila.client.backend

import app.cash.backfila.embedded.Backfila
import com.google.inject.Module
import javax.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class NoBackfillsTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = NoBackfillsModule()

  @Inject lateinit var backfila: Backfila

  @Test fun `configure service is sent when there are no registered backfills`() {
    checkNotNull(backfila.configureServiceData)
    assert(backfila.configureServiceData!!.backfills.isEmpty())
  }
}
