package app.cash.backfila.service.selfbackfill

import app.cash.backfila.client.BackfilaClientConfig
import app.cash.backfila.client.BackfilaClientLoggingSetupProvider
import app.cash.backfila.client.BackfilaClientNoLoggingSetupProvider
import app.cash.backfila.client.BackfilaClientServiceClientProvider
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.client.internal.BackfilaClient
import app.cash.backfila.client.misk.hibernate.HibernateBackfillModule
import app.cash.backfila.client.misk.internal.BackfilaStartupService
import app.cash.backfila.service.persistence.BackfilaDb
import misk.ServiceModule
import misk.inject.KAbstractModule
import misk.jdbc.SchemaMigratorService

// Connector type used only by backfila to backfill itself.
private const val LOCAL = "LOCAL"

class LocalBackfillingModule : KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<BackfilaStartupService>()
        .dependsOn<SchemaMigratorService>(BackfilaDb::class),
    )

    bind(BackfilaClientConfig::class.java).toInstance(
      BackfilaClientConfig(
        slack_channel = null,
        connector_type = LOCAL,
        connector_extra_data = "",
        variant = null,
      ),
    )

    newMapBinder<String, BackfilaClientServiceClientProvider>(ForConnectors::class)
      .addBinding(LOCAL)
      .to(LocalClientServiceClientProvider::class.java)

    bind(BackfilaClient::class.java).to(LocalBackfilaClient::class.java)
    bind(BackfilaClientLoggingSetupProvider::class.java)
      .to(BackfilaClientNoLoggingSetupProvider::class.java)

    install(HibernateBackfillModule.create<BackfillRegisteredParameters>())
  }
}
