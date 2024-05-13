package app.cash.backfila.client.internal

import app.cash.backfila.client.BackfilaClientConfig
import app.cash.backfila.client.spi.BackfilaParametersOperator.Companion.backfilaParametersFromClass
import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillRegistration
import app.cash.backfila.protos.service.ConfigureServiceRequest
import javax.inject.Inject
import javax.inject.Singleton
import wisp.logging.getLogger

/**
 * Sends backfill metadata to Backfila at application startup. If Backfila is unreachable then
 * this will fail silently and backfills will not be updated until the next time the service starts.
 */
@Singleton
class BackfilaStartupConfigurator @Inject constructor(
  private val config: BackfilaClientConfig,
  private val backfilaClient: BackfilaClient,
  private val backends: Set<BackfillBackend>,
) {
  fun sendBackfillMetadataToBackfila() {
    logger.info { "Backfila configurator starting" }

    // Build a list of registered Backfills from the different client backend implementations.
    val registrations = mutableSetOf<BackfillRegistration>()
    for (backend in backends) {
      val backfills = backend.backfills()
      // Make sure each backfill name is ony registered once in the service.
      val intersection = registrations.map { it.name }.intersect(backfills.map { it.name })
      require(intersection.isEmpty()) { "Found duplicate registrations: $intersection " }

      registrations.addAll(backfills)
    }

    // Send the Backfila registration request
    val request: ConfigureServiceRequest? = if (backfilaClient.throwOnStartup) {
      getRequest(registrations).getOrThrow()
    } else {
      getRequest(registrations).getOrElse {
        logger.error(it.cause) { "Exception generating backfila registration request, skipped!" }
        null
      }
    }

    // TODO(mikepaw): make this async.
    if (request != null) {
      if (backfilaClient.throwOnStartup) {
        sendConfigureRequest(request, registrations).getOrThrow()
      } else {
        sendConfigureRequest(request, registrations).getOrElse {
          logger.error(it.cause) { "Exception making startup call to configure backfila, skipped!" }
        }
      }
    }
  }

  private fun sendConfigureRequest(request: ConfigureServiceRequest, registrations: MutableSet<BackfillRegistration>) = kotlin.runCatching {
    backfilaClient.configureService(request)

    logger.info {
      "Backfila lifecycle listener initialized. " +
        "Updated backfila with ${registrations.size} backfills."
    }
  }

  private fun getRequest(registrations: MutableSet<BackfillRegistration>): Result<ConfigureServiceRequest> = kotlin.runCatching {
    ConfigureServiceRequest.Builder()
      .backfills(
        registrations.map { registration ->
          @Suppress("UNCHECKED_CAST")
          val parameters = backfilaParametersFromClass(registration.parametersClass)
          ConfigureServiceRequest.BackfillData.Builder()
            .name(registration.name)
            .description(registration.description)
            .parameters(parameters)
            .delete_by(registration.deleteBy?.toEpochMilli())
            .build()
        },
      )
      .connector_type(config.connector_type)
      .connector_extra_data(config.connector_extra_data)
      .slack_channel(config.slack_channel)
      .variant(config.variant)
      .build()
  }

  companion object {
    val logger = getLogger<BackfilaStartupConfigurator>()
  }
}
