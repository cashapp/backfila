package app.cash.backfila.service.selfbackfill

import app.cash.backfila.client.BackfilaClientServiceClient
import app.cash.backfila.client.BackfilaClientServiceClientProvider
import app.cash.backfila.client.BackfilaDefaultEndpointConfigModule
import app.cash.backfila.client.ForConnectors
import app.cash.backfila.dashboard.ViewLogsUrlProvider
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.KeyRange
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfilaServiceModule
import app.cash.backfila.service.persistence.DbBackfillRun
import misk.MiskApplication
import misk.MiskCaller
import misk.MiskRealServiceModule
import misk.environment.DeploymentModule
import misk.hibernate.Session
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.security.authz.DevelopmentOnly
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.dashboard.AdminDashboardModule
import okio.ByteString.Companion.encodeUtf8
import wisp.deployment.Deployment

internal fun main(args: Array<String>) {
  val deployment = Deployment(name = "backfila", isLocalDevelopment = true)

  MiskApplication(
    object : KAbstractModule() {
      override fun configure() {
        val webConfig = WebConfig(
          port = 8080,
          idle_timeout = 500000,
          host = "0.0.0.0",
        )
        install(MiskWebModule(webConfig))
        multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
        bind<MiskCaller>().annotatedWith<DevelopmentOnly>()
          .toInstance(MiskCaller(user = "testfila"))
        bind<ViewLogsUrlProvider>().to<DevelopmentViewLogsUrlProvider>()

        newMapBinder<String, BackfilaClientServiceClientProvider>(ForConnectors::class)
          .permitDuplicates().addBinding("DEV")
          .toInstance(object : BackfilaClientServiceClientProvider {
            override fun validateExtraData(connectorExtraData: String?) {
            }

            override fun clientFor(
              serviceName: String,
              connectorExtraData: String?,
            ): BackfilaClientServiceClient {
              return object : BackfilaClientServiceClient {
                override fun prepareBackfill(request: PrepareBackfillRequest): PrepareBackfillResponse {
                  return PrepareBackfillResponse.Builder()
                    .partitions(
                      listOf(
                        PrepareBackfillResponse.Partition(
                          "-80", KeyRange("0".encodeUtf8(), "1000".encodeUtf8()), null,
                        ),
                        PrepareBackfillResponse.Partition(
                          "80-", KeyRange("0".encodeUtf8(), "1000".encodeUtf8()), null,
                        ),
                      ),
                    ).build()
                }

                override suspend fun getNextBatchRange(request: GetNextBatchRangeRequest): GetNextBatchRangeResponse {
                  TODO("Not yet implemented")
                }

                override suspend fun runBatch(request: RunBatchRequest): RunBatchResponse {
                  TODO("Not yet implemented")
                }
              }
            }
          },
          )
      }
    },
    DeploymentModule(deployment),
    BackfilaServiceModule(
      deployment,
      BackfilaConfig(
        backfill_runner_threads = null,
        data_source_clusters = DataSourceClustersConfig(
          mapOf(
            "backfila-001" to DataSourceClusterConfig(
              writer = DataSourceConfig(
                type = DataSourceType.MYSQL,
                database = System.getenv("BACKFILA_DB_NAME") ?: "backfila_development",
                username = System.getenv("BACKFILA_DB_USER") ?: "root",
                migrations_resource = "classpath:/migrations",
                host = System.getenv("BACKFILA_DB_HOST") ?: "127.0.0.1",
                port = (System.getenv("BACKFILA_DB_PORT") ?: "3306").toInt(),
                password = System.getenv("BACKFILA_DB_PASSWORD"),
              ),
              reader = null,
            ),
          ),
        ),
        web_url_root = "http://localhost:8080/app/",
        slack = null,
      ),
    ),
    AdminDashboardModule(isDevelopment = true),
    LocalBackfillingModule(),
    BackfilaDefaultEndpointConfigModule(),
    MiskRealServiceModule(),
  ).run(args)
}

internal class DevelopmentViewLogsUrlProvider : ViewLogsUrlProvider {
  override fun getUrl(session: Session, backfillRun: DbBackfillRun): String {
    return "/"
  }
}
