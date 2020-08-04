package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.BackfilaApi
import app.cash.backfila.client.Connectors
import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.BackfillRun
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.ConfigureServiceResponse
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillResponse
import okio.ByteString
import retrofit2.Call
import retrofit2.mock.Calls
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

/**
 * A small implementation of Backfila suitable for use in test cases and development mode. Unlike
 * the full-sized Backfila this doesn't connect to a remote Backfila service. This loses all
 * backfill state when the service is restarted.
 */
@Singleton
internal class EmbeddedBackfila @Inject internal constructor(
  private val operatorFactory: BackfillOperator.Factory
) : Backfila, BackfilaApi {
  private var serviceData: ConfigureServiceRequest? = null
  private var backfillIdGenerator = AtomicInteger(10)

  override fun configureService(request: ConfigureServiceRequest): Call<ConfigureServiceResponse> {
    check(serviceData == null) { "Should only be configuring a single backfila service." }
    check(request.connector_type == Connectors.HTTP) { "Misk client only supports HTTP." }
    serviceData = request
    return Calls.response(ConfigureServiceResponse())
  }

  override fun createAndStartbackfill(
    request: CreateAndStartBackfillRequest
  ): Call<CreateAndStartBackfillResponse> {
    checkNotNull(serviceData) { "Must register the service before creating a backfill" }

    val createRequest = request.create_request
    checkNotNull(serviceData!!.backfills.firstOrNull { it.name == createRequest.backfill_name }) {
      "Backfill ${createRequest.backfill_name} was not registered properly"
    }

    val backfillId = backfillIdGenerator.getAndIncrement()
    val operator = operatorFactory.create(createRequest.backfill_name, backfillId.toString())

    val run = EmbeddedBackfillRun<Backfill<*, *, *>>(
        operator = operator,
        dryRun = createRequest.dry_run,
        parameters = createRequest.parameter_map,
        rangeStart = createRequest.pkey_range_start?.utf8(),
        rangeEnd = createRequest.pkey_range_end?.utf8()
    )

    run.execute()

    return Calls.response(CreateAndStartBackfillResponse(backfillId.toLong()))
  }

  override fun <Type : Backfill<*, *, *>> createDryRun(
    backfill: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ) = createBackfill(backfill, true, parameters, rangeStart, rangeEnd)

  override fun <Type : Backfill<*, *, *>> createWetRun(
    backfillType: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ) = createBackfill(backfillType, false, parameters, rangeStart, rangeEnd)

  private fun <Type : Backfill<*, *, *>> createBackfill(
    backfillType: KClass<Type>,
    dryRun: Boolean,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ): BackfillRun<Type> {
    checkNotNull(serviceData) { "Must register the service before creating a backfill" }
    check(serviceData!!.backfills.map { it.name }.contains(backfillType.jvmName)) {
      "Backfill ${backfillType.jvmName} was not registered properly"
    }

    val backfillId = backfillIdGenerator.getAndIncrement().toString()
    val operator = operatorFactory.create(backfillType.jvmName, backfillId)

    @Suppress("UNCHECKED_CAST") // We don't know the types statically, so fake them.
    return EmbeddedBackfillRun(
        operator = operator,
        dryRun = dryRun,
        parameters = parameters,
        rangeStart = rangeStart,
        rangeEnd = rangeEnd
    )
  }
}
