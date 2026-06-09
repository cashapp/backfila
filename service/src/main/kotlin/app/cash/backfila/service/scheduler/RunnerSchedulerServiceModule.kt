package app.cash.backfila.service.scheduler

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule

class RunnerSchedulerServiceModule : KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<RunnerSchedulerService>()
        .dependsOn<ReadyService>(),
    )
  }
}
