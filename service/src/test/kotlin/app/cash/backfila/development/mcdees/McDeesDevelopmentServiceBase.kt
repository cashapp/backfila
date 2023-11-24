package app.cash.backfila.development.mcdees

import app.cash.backfila.development.BackfilaDevelopmentLogging
import app.cash.backfila.development.DevServiceConstants.Companion.BACKFILA_PORT
import app.cash.backfila.development.ServiceHeaderInterceptor
import misk.MiskApplication
import misk.MiskRealServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.config.Config
import misk.config.ConfigModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.dashboard.AdminDashboardModule
import okhttp3.Interceptor
import wisp.deployment.Deployment

/**
 * A fake development service that flips burgers and deals in beef. Also has Canadian and US operations(variants).
 */
class McDeesDevelopmentServiceBase {
  fun runMcDees(
    variant: String,
    port: Int,
    args: Array<String>,
  ) {
    BackfilaDevelopmentLogging.configure()

    val deployment = Deployment(name = "McDees", isLocalDevelopment = true)
    val config = McDeesConfig()

    MiskApplication(
      object : KAbstractModule() {
        override fun configure() {
          val webConfig = WebConfig(
            port = port,
            idle_timeout = 500000,
            host = "0.0.0.0",
          )
          install(MiskWebModule(webConfig))
          multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
          install(ConfigModule.create("McDees", config))
          multibind<Interceptor>().toInstance(ServiceHeaderInterceptor("McDees"))
          install(
            HttpClientsConfigModule(
              HttpClientsConfig(
                endpoints = mapOf(
                  "backfila" to HttpClientEndpointConfig(url = "http://localhost:$BACKFILA_PORT/"),
                ),
              ),
            ),
          )
        }
      },
      DeploymentModule(deployment),
      McDeesServiceModule(
        variant = variant,
        port = port,
      ),
      AdminDashboardModule(isDevelopment = true),
      MiskRealServiceModule(),
    ).run(args)
  }

  internal data class McDeesConfig(
    val chef: String = "The Hamburgler",
  ) : Config
}
