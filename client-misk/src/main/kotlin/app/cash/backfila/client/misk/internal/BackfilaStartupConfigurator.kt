package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.Connectors
import app.cash.backfila.client.HttpConnectorData
import app.cash.backfila.client.misk.ForBackfila
import app.cash.backfila.client.misk.client.BackfilaClientConfig
import app.cash.backfila.client.misk.internal.BackfilaParametersOperator.Companion.backfilaParametersFromClass
import app.cash.backfila.protos.service.ConfigureServiceRequest
import com.google.common.util.concurrent.AbstractIdleService
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton
import misk.logging.getLogger
import misk.moshi.adapter

/**
 * Sends backfill metadata to Backfila at application startup. If Backfila is unreachable then
 * this will fail silently and backfills will not be updated until the next time the service starts.
 */
@Singleton
internal class BackfilaStartupConfigurator @Inject internal constructor(
  private val config: BackfilaClientConfig,
  private val backfilaClient: BackfilaClient,
  @ForBackfila private val moshi: Moshi,
  private val backends: Set<BackfillOperator.Backend>
) : AbstractIdleService() {
  override fun startUp() {
    logger.info { "Backfila configurator starting" }

    val connectorDataAdapter = moshi.adapter<HttpConnectorData>()
    val httpConnectorData = HttpConnectorData(url = config.url)

    // Build a list of registered Backfills from the different client backend implementations.
    val registrations = mutableSetOf<BackfillOperator.BackfillRegistration>()
    for (backend in backends) {
      val backfills = backend.backfills()
      // Make sure each backfill name is ony registered once in the service.
      val intersection = registrations.map { it.name }.intersect(backfills.map { it.name })
      require(intersection.isEmpty()) { "Found duplicate registrations: $intersection " }

      registrations.addAll(backfills)
    }

    // Send the Backfila registration request
    val request = ConfigureServiceRequest.Builder()
        .backfills(
            registrations.map { registration ->
              @Suppress("UNCHECKED_CAST")
              val parameters = backfilaParametersFromClass(registration.parametersClass)
              ConfigureServiceRequest.BackfillData.Builder()
                  .name(registration.name)
                  .description(registration.description)
                  .parameters(parameters)
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
            "Updated backfila with ${registrations.size} backfills."
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
