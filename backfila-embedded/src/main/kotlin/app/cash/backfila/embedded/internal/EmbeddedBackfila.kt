package app.cash.backfila.client.internal

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.Backfill
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.EnvoyConnectorData
import app.cash.backfila.client.HttpConnectorData
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.BackfillRun
import app.cash.backfila.embedded.internal.EmbeddedBackfillRun
import app.cash.backfila.protos.service.CheckBackfillStatusRequest
import app.cash.backfila.protos.service.CheckBackfillStatusResponse
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import okio.ByteString
import retrofit2.Call
import retrofit2.mock.Calls

/**
 * A small implementation of Backfila suitable for use in test cases and development mode. Unlike
 * the full-sized Backfila this doesn't connect to a remote Backfila service. This loses all
 * backfill state when the service is restarted.
 */
@Singleton
internal class EmbeddedBackfila @Inject internal constructor(
  private val operatorFactory: BackfillOperatorFactory,
) : Backfila, BackfilaApi {
  private var serviceData: ConfigureServiceRequest? = null
  private var backfillRunIdGenerator = AtomicInteger(10)
  private var createdBackfillRuns = mutableMapOf<String, BackfillRun<*>>()

  override val configureServiceData: ConfigureServiceRequest?
    get() = serviceData

  override fun configureService(request: ConfigureServiceRequest): Call<ConfigureServiceResponse> {
    check(serviceData == null) { "Should only be configuring a single service for backfila." }
    // Creating a local moshi to do the quick config conversion.
    val moshi = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
    when (request.connector_type) {
      Connectors.HTTP -> {
        val connectorDataAdapter = moshi.adapter(HttpConnectorData::class.java)
        val httpData = connectorDataAdapter.fromJson(request.connector_extra_data)
        checkNotNull(httpData) { "Must provide HTTP connector data for HTTP connector type." }
      }
      Connectors.ENVOY -> {
        val connectorDataAdapter = moshi.adapter(EnvoyConnectorData::class.java)
        // The data can be null, however, we still check that it parses without an error.
        connectorDataAdapter.fromJson(request.connector_extra_data)
      }
      else -> error("Backfila only supports HTTP and Envoy currently.")
    }
    serviceData = request
    return Calls.response(ConfigureServiceResponse())
  }

  override fun createAndStartbackfill(
    request: CreateAndStartBackfillRequest,
  ): Call<CreateAndStartBackfillResponse> {
    checkNotNull(serviceData) { "Must register the service before creating a backfill" }

    val createRequest = request.create_request
    checkNotNull(serviceData!!.backfills.firstOrNull { it.name == createRequest.backfill_name }) {
      "Backfill ${createRequest.backfill_name} was not registered properly"
    }

    val backfillRunId = backfillRunIdGenerator.getAndIncrement().toString()
    val operator = operatorFactory.create(createRequest.backfill_name, backfillRunId)

    val run = EmbeddedBackfillRun<Backfill>(
      operator = operator,
      dryRun = createRequest.dry_run,
      parameters = createRequest.parameter_map.toMutableMap(),
      rangeStart = createRequest.pkey_range_start?.utf8(),
      rangeEnd = createRequest.pkey_range_end?.utf8(),
      backfillRunId = backfillRunId,
    )
    createdBackfillRuns[backfillRunId] = run

    run.execute()

    return Calls.response(CreateAndStartBackfillResponse(backfillRunId.toLong()))
  }

  override fun checkBackfillStatus(request: CheckBackfillStatusRequest): Call<CheckBackfillStatusResponse> {
    checkNotNull(request.backfill_run_id)
    val backfillRun = createdBackfillRuns[request.backfill_run_id.toString()] ?: error("No Backfill with id ${request.backfill_run_id} found")
    // If the backfill isn't complete it is considered running for the purposes of EmbeddedBackfila.
    return Calls.response(
      CheckBackfillStatusResponse(
        if (backfillRun.complete()) CheckBackfillStatusResponse.Status.COMPLETE else CheckBackfillStatusResponse.Status.RUNNING,
      ),
    )
  }

  override fun <Type : Backfill> createDryRun(
    backfill: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?,
  ) = createBackfill(backfill, true, parameters, rangeStart, rangeEnd)

  override fun <Type : Backfill> createWetRun(
    backfillType: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?,
  ) = createBackfill(backfillType, false, parameters, rangeStart, rangeEnd)

  override fun <Type : Backfill> findExistingRun(backfillType: KClass<Type>, backfillRunId: Long): BackfillRun<Type> {
    val untypedBackfill = createdBackfillRuns[backfillRunId.toString()] ?: error("No Backfill with id $backfillRunId found")
    check(untypedBackfill.backfill::class == backfillType) {
      "Backfill with run id $backfillRunId is not of type $backfillType"
    }
    @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
    return untypedBackfill as BackfillRun<Type>
  }

  override fun <Type : Backfill> findLatestRun(backfillType: KClass<Type>): BackfillRun<Type> {
    val untypedBackfill = createdBackfillRuns
      .filter { it.value.backfill::class == backfillType }
      .maxByOrNull { it.key.toLong() }
      ?.value
      ?: error("No latest backfill of type $backfillType found")
    @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
    return untypedBackfill as BackfillRun<Type>
  }

  private fun <Type : Backfill> createBackfill(
    backfillType: KClass<Type>,
    dryRun: Boolean,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?,
  ): BackfillRun<Type> {
    checkNotNull(serviceData) { "Must register the service before creating a backfill" }
    check(serviceData!!.backfills.map { it.name }.contains(backfillType.jvmName)) {
      "Backfill ${backfillType.jvmName} was not registered properly"
    }

    val backfillRunId = backfillRunIdGenerator.getAndIncrement().toString()
    val operator = operatorFactory.create(backfillType.jvmName, backfillRunId)

    val backfillRun = EmbeddedBackfillRun<Type>(
      operator = operator,
      dryRun = dryRun,
      parameters = parameters.toMutableMap(),
      rangeStart = rangeStart,
      rangeEnd = rangeEnd,
      backfillRunId = backfillRunId,
    )
    createdBackfillRuns[backfillRunId] = backfillRun
    return backfillRun
  }
}
