package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaHttpClientConfig
import app.cash.backfila.client.misk.MiskBackfillModule
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.inject.KAbstractModule
import misk.security.authz.AccessControlModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.WebServerTestingModule

class CallbackAccessTestingModule(
  private val config: BackfilaHttpClientConfig,
) : KAbstractModule() {
  override fun configure() {
    install(WebServerTestingModule())
    install(MiskTestingServiceModule())
    install(AccessControlModule())
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
    install(
      HttpClientsConfigModule(
        HttpClientsConfig(
          endpoints = mapOf("backfila" to HttpClientEndpointConfig(url = "http://localhost")),
        ),
      ),
    )
    install(MiskBackfillModule(config))
    install(BackfilaMiskClientModule())
  }
}
