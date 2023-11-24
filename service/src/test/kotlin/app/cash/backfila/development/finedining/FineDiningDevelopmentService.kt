package app.cash.backfila.development.finedining

import app.cash.backfila.development.BackfilaDevelopmentLogging
import app.cash.backfila.development.DevServiceConstants.Companion.BACKFILA_PORT
import app.cash.backfila.development.DevServiceConstants.Companion.FINE_DINING_PORT
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
 * A fake development service that takes a long time to do anything.
 */
fun main(args: Array<String>) {
  BackfilaDevelopmentLogging.configure()

  val deployment = Deployment(name = "FineDining", isLocalDevelopment = true)
  val config = FineDiningConfig()

  MiskApplication(
    object : KAbstractModule() {
      override fun configure() {
        val webConfig = WebConfig(
          port = FINE_DINING_PORT,
          idle_timeout = 500000,
          host = "0.0.0.0",
        )
        install(MiskWebModule(webConfig))
        multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
        install(ConfigModule.create("FineDining", config))
        multibind<Interceptor>().toInstance(ServiceHeaderInterceptor("FineDining"))
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
    FineDiningServiceModule(),
    AdminDashboardModule(isDevelopment = true),
    MiskRealServiceModule(),
  ).run(args)
}

data class FineDiningConfig(
  val chef: String = "Julia Child",
) : Config
