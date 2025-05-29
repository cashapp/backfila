package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.ClientMiskTestingModule
import com.google.inject.Module
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess

@MiskTest(startService = true)
class VitessUnshardedHibernateBackfillTest : SinglePartitionHibernateBackfillTest() {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(true)

  @MiskExternalDependency
  private val dockerVitess = DockerVitess()
}
