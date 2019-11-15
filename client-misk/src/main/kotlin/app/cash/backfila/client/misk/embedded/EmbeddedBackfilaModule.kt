package app.cash.backfila.client.misk.embedded

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.misk.internal.EmbeddedBackfila
import misk.inject.KAbstractModule

/**
 * Use this to connect to an embedded Backfila implementation in development or testing. You will
 * also need to install a [BackfilaModule].
 */
class EmbeddedBackfilaModule: KAbstractModule() {
  override fun configure() {
    bind<BackfilaApi>().to<EmbeddedBackfila>()
    bind<Backfila>().to<EmbeddedBackfila>()
  }
}