package com.squareup.backfila.service

import com.squareup.skim.config.SkimConfig
import misk.MiskServiceModule
import misk.config.ConfigModule
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.KAbstractModule

class BackfilaModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    val config = SkimConfig.load<BackfilaConfig>("backfila", environment)
    install(ConfigModule.create("backfila", config))

    install(EnvironmentModule(environment))
  }
}
