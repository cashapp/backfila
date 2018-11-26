package com.squareup.backfila.actions

import com.google.inject.Module
import com.squareup.backfila.dashboard.GetDashboardAction
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class GetDashboardActionTest {

  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaWebActionTestingModule()

  @Inject lateinit var getDashboardAction: GetDashboardAction

  @Test
  fun getDashboard() {
    val dashboard = getDashboardAction.loadDashboard()
    assertThat(dashboard.backfills[0].name).isEqualTo("BackfillRavenTemplates")
  }
}
