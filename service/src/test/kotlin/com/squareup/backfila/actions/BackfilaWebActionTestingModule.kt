package com.squareup.backfila.actions

import com.squareup.backfila.dashboard.DashboardModule
import com.squareup.backfila.service.BackfilaModule
import misk.MiskTestingServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

internal class BackfilaWebActionTestingModule : KAbstractModule() {
  override fun configure() {
    install(EnvironmentModule(Environment.TESTING))
    install(LogCollectorModule())
    install(MiskTestingServiceModule())
    install(BackfilaModule(Environment.TESTING))
    install(DashboardModule())
  }
}
