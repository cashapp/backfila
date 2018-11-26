package com.squareup.backfila.actions

import com.squareup.backfila.service.BackfilaModule
import misk.MiskServiceModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule

internal class BackfilaWebActionTestingModule : KAbstractModule() {
  override fun configure() {
    install(EnvironmentModule(Environment.TESTING))
    install(LogCollectorModule())
    install(MiskServiceModule())
    install(BackfilaModule(Environment.TESTING))
  }
}
