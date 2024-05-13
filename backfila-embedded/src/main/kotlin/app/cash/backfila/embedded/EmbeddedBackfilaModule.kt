package app.cash.backfila.embedded

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.OnStartup
import app.cash.backfila.client.internal.EmbeddedBackfila
import com.google.inject.AbstractModule

/**
 * Use this to connect to an embedded Backfila implementation in development or testing. You will
 * also need to install a [BackfillModule].
 */
class EmbeddedBackfilaModule
@JvmOverloads constructor(private val onStartup: OnStartup = OnStartup.THROW_ON_STARTUP) : AbstractModule() {
  override fun configure() {
    bind(BackfilaApi::class.java).to(EmbeddedBackfila::class.java)
    bind(Backfila::class.java).to(EmbeddedBackfila::class.java)
    bind(OnStartup::class.java).toInstance(onStartup)
  }
}
