package app.cash.backfila.client.misk

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.Connectors
import app.cash.backfila.protos.service.ConfigureServiceRequest
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.BindingAnnotation
import com.google.inject.Key
import com.google.inject.Provides
import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import misk.ServiceModule
import misk.client.TypedHttpClientModule
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.web.WebActionModule
import retrofit2.Retrofit
import retrofit2.converter.wire.WireConverterFactory
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class BackfilaClientConfig(
  val slack_channel: String?
)

@BindingAnnotation
annotation class ForBackfila

class BackfilaClientModule(
  private val config: BackfilaClientConfig,
  private val backfills: List<Class<out Backfill>>
) : KAbstractModule() {
  override fun configure() {
    bind<BackfilaClientConfig>().toInstance(config)

    bind<BackfilaClient>().to<RealBackfilaClient>()
    install(TypedHttpClientModule(BackfilaApi::class,
        name = "backfila",
        retrofitBuilderProvider = getProvider(
            Key.get(Retrofit.Builder::class.java, Names.named("wire")))))

    val map = mutableMapOf<String, Class<out Backfill>>()
    for (backfill in backfills) {
      map[backfill.name] = backfill
    }
    bind(object : TypeLiteral<Map<String, Class<out Backfill>>>() {})
        .annotatedWith(ForBackfila::class.java)
        .toInstance(map)

//    val newMapBinder = newMapBinder<String, Class<out Backfill>>(ForBackfila::class)
    for (backfill in backfills) {
//      newMapBinder
//          .addBinding(backfill.name).toInstance(backfill)
    }

    install(ServiceModule<BackfilaStartupConfigurator>())

    install(WebActionModule.create<PrepareBackfillAction>())
    install(WebActionModule.create<GetNextBatchRangeAction>())
    install(WebActionModule.create<RunBatchAction>())
  }

  @Provides @Named("wire") fun wireRetrofitBuilder() =
      Retrofit.Builder().addConverterFactory(WireConverterFactory.create())
}

@Singleton
internal class BackfilaStartupConfigurator @Inject internal constructor(
  private val config: BackfilaClientConfig,
  private val backfilaClient: BackfilaClient,
  @ForBackfila private val backfills: Map<String, Class<out Backfill>>
) : AbstractIdleService() {
  override fun startUp() {
    logger.info { "Backfila configurator starting" }

    val request = ConfigureServiceRequest.Builder()
        .backfills(
            backfills.values.map { backfillClass ->
              ConfigureServiceRequest.BackfillData.Builder()
                  .name(backfillClass.name)
                  .build()
            })
        .connector_type(Connectors.HTTP)
        .slack_channel(config.slack_channel)
        .build()

    try {
      backfilaClient.configureService(request)

      logger.info {
        "Backfila lifecycle listener initialized. " +
            "Updated backfila with ${backfills.size} backfills."
      }
    } catch (e: Exception) {
      logger.warn(e) { "Exception making startup call to configure backfila, skipped!" }
    }
  }

  override fun shutDown() {
  }

  companion object {
    val logger = getLogger<BackfilaStartupConfigurator>()
  }
}
