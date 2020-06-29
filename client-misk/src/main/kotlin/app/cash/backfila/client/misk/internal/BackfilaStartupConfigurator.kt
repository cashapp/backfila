package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.Connectors
import app.cash.backfila.client.HttpConnectorData
import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.ForBackfila
import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.protos.service.ConfigureServiceRequest
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Injector
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import misk.logging.getLogger
import misk.moshi.adapter

/**
 * Sends backfill metadata to Backfila at application startup. If Backfila is unreachable then
 * this will fail silently and backfills will not be updated until the next time the service starts.
 */
@Singleton
internal class BackfilaStartupConfigurator @Inject internal constructor(
  private val injector: Injector,
  private val config: BackfilaClientConfig,
  private val backfilaClient: BackfilaClient,
  @ForBackfila private val moshi: Moshi,
  @ForBackfila private val backfills: MutableMap<String, KClass<out Backfill<*, *>>>
) : AbstractIdleService() {
  override fun startUp() {
    logger.info { "Backfila configurator starting" }

    val connectorDataAdapter = moshi.adapter<HttpConnectorData>()
    val httpConnectorData = HttpConnectorData(url = config.url)

    val request = ConfigureServiceRequest.Builder()
        .backfills(
            backfills.values.map { backfillClass ->
              // Create an instance of the Backfill so we can ask it what its parameters are. This
              // is a bit of a hack because we're creating the backfill object but not running a
              // backfill with it.
              val backfill = injector.getInstance(backfillClass.java)
              ConfigureServiceRequest.BackfillData.Builder()
                  .name(backfillClass.jvmName)
                  .parameters(backfill.parameters)
                  .build()
            })
        .connector_type(Connectors.HTTP)
        .connector_extra_data(connectorDataAdapter.toJson(httpConnectorData))
        .slack_channel(config.slack_channel)
        .build()

    // TODO(mikepaw): make this async.
    try {
      backfilaClient.configureService(request)

      logger.info {
        "Backfila lifecycle listener initialized. " +
            "Updated backfila with ${backfills.size} backfills."
      }
    } catch (e: Exception) {
      logger.error(e) { "Exception making startup call to configure backfila, skipped!" }
    }
  }

  override fun shutDown() {
  }

  companion object {
    val logger = getLogger<BackfilaStartupConfigurator>()
  }
}
