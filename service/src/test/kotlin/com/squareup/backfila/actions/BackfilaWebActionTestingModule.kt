package com.squareup.backfila.actions

import com.squareup.backfila.dashboard.DashboardModule
import com.squareup.skim.SkimTestingModule
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
    install(DashboardModule())
  }
}
