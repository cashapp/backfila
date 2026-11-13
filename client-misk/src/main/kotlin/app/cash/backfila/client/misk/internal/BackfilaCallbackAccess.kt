package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.BackfilaClientConfig
import com.google.inject.Provider
import jakarta.inject.Inject
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry

/** Limits the Backfila callback endpoints to [BackfilaClientConfig.callback_services]. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
internal annotation class BackfilaCallbackAccess

internal class BackfilaCallbackAccessModule : KAbstractModule() {
  override fun configure() {
    multibind<AccessAnnotationEntry>().toProvider(BackfilaCallbackAccessEntryProvider::class.java)
  }
}

internal class BackfilaCallbackAccessEntryProvider @Inject constructor(
  private val config: BackfilaClientConfig,
) : Provider<AccessAnnotationEntry> {
  override fun get(): AccessAnnotationEntry {
    require(config.callback_services.isNotEmpty()) { "callback_services must not be empty" }
    return AccessAnnotationEntry<BackfilaCallbackAccess>(services = config.callback_services)
  }
}
