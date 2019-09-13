package app.cash.backfila.service

import misk.ServiceModule
import misk.hibernate.SchemaMigratorService
import misk.inject.KAbstractModule

class SchedulerLifecycleServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<RunnerSchedulerService>()
        .dependsOn<SchemaMigratorService>(BackfilaDb::class))
  }
}