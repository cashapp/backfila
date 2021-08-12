package app.cash.backfila.embedded

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.internal.EmbeddedBackfila
import com.google.inject.AbstractModule
import misk.inject.KAbstractModule

/**
 * Use this to connect to an embedded Backfila implementation in development or testing. You will
 * also need to install a [BackfillModule].
 */
class EmbeddedBackfilaModule : KAbstractModule() {
  override fun configure() {
    bind<BackfilaApi>().to<EmbeddedBackfila>()
    bind<Backfila>().to<EmbeddedBackfila>()
  }
}
