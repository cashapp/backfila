package app.cash.backfila.service.scheduler

import app.cash.backfila.service.persistence.BackfilaDb
import misk.ServiceModule
import misk.hibernate.SchemaMigratorService
import misk.inject.KAbstractModule

class RunnerSchedulerServiceModule : KAbstractModule() {
  override fun configure() {
    install(ServiceModule<RunnerSchedulerService>()
        .dependsOn<SchemaMigratorService>(BackfilaDb::class))
  }
}
