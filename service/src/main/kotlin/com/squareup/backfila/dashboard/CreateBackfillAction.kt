package com.squareup.backfila.dashboard

import com.squareup.backfila.client.ConnectorProvider
import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.BackfillState
import com.squareup.backfila.service.DbBackfillRun
import com.squareup.backfila.service.DbRegisteredBackfill
import com.squareup.backfila.service.DbRunInstance
import com.squareup.backfila.service.DbService
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import com.squareup.protos.backfila.clientservice.KeyRange
import com.squareup.protos.backfila.clientservice.PrepareBackfillRequest
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import java.net.HttpURLConnection
import javax.inject.Inject

data class CreateBackfillRequest(
  val backfill_name: String,
  // TODO move defaults to UI
  val scan_size: Long = 1000,
  val batch_size: Long = 100,
  val num_threads: Int = 5,
  val pkey_range_start: String? = null,
  val pkey_range_end: String? = null,
  // Parameters that go to the client service.
  val parameter_map: Map<String, ByteString> = mapOf(),
  val dry_run: Boolean = false,
  val backoff_schedule: String? = null
)

class CreateBackfillAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val connectorProvider: ConnectorProvider
) : WebAction {

  @Post("/services/{service}/create")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO allow any user
  @Authenticated(capabilities = ["eng"])
  fun create(
    @PathParam service: String,
    @RequestBody request: CreateBackfillRequest
  ): Response<ResponseBody> {
    // TODO check user has permissions for this service with `X-Forwarded-All-Capabilities` header

    logger.info { "Create backfill for $service by ${caller.get()?.user}" }

    if (request.num_threads < 1) {
      throw BadRequestException("num_threads must be >= 1")
    }
    if (request.scan_size < 1) {
      throw BadRequestException("scan_size must be >= 1")
    }
    if (request.batch_size < 1) {
      throw BadRequestException("batch_size must be >= 1")
    }
    if (request.scan_size < request.batch_size) {
      throw BadRequestException("scan_size must be >= batch_size")
    }
    request.backoff_schedule?.let { schedule ->
      if (schedule.split(',').any { it.toLongOrNull() == null }) {
        throw BadRequestException("backoff_schedule must be a comma separated list of integers")
      }
    }

    val dbData = transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(service)
          .uniqueResult(session) ?: throw BadRequestException("`$service` doesn't exist")
      val registeredBackfill = queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .name(request.backfill_name)
          .active()
          .uniqueResult(session)
          ?: throw BadRequestException("`${request.backfill_name}` doesn't exist")
      logger.info {
        "Found registered backfill for `$service`::`${request.backfill_name}`" +
            " [id=${registeredBackfill.id}]"
      }
      DbData(
          dbService.id,
          dbService.connector,
          dbService.connector_extra_data,
          registeredBackfill.id
      )
    }

    val client = connectorProvider.clientProvider(dbData.connectorType)
        .clientFor(service, dbData.connectorExtraData)
    val prepareBackfillResponse =
        client.prepareBackfill(PrepareBackfillRequest(
            dbData.registeredBackfillId.toString(),
            request.backfill_name,
            KeyRange(request.pkey_range_start?.encodeUtf8(), request.pkey_range_end?.encodeUtf8()),
            request.parameter_map,
            request.dry_run
        ))
    val instances = prepareBackfillResponse.instances
    if (instances.isEmpty()) {
      throw BadRequestException("PrepareBackfill returned no instances")
    }
    if (instances.distinctBy { it.instance_name }.size != instances.size) {
      throw BadRequestException("PrepareBackfill did not return distinct instance names:" +
          " ${instances.map { it.instance_name }}")
    }

    // TODO validate params fit names

    val backfillRunId = transacter.transaction { session ->
      val backfillRun = DbBackfillRun(
          dbData.serviceId,
          dbData.registeredBackfillId,
          request.parameter_map,
          BackfillState.PAUSED,
          caller.get()?.user,
          request.scan_size,
          request.batch_size,
          request.num_threads,
          request.backoff_schedule
      )
      session.save(backfillRun)

      for (instance in instances) {
        val dbRunInstance = DbRunInstance(
            backfillRun.id,
            instance.instance_name,
            instance.backfill_range,
            backfillRun.state,
            instance.estimated_record_count
        )
        session.save(dbRunInstance)
      }

      backfillRun.id
    }

    return Response(
        body = "go to /backfills/$backfillRunId".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = headersOf("Location", "/backfills/$backfillRunId")
    )
  }

  data class DbData(
    val serviceId: Id<DbService>,
    val connectorType: String,
    val connectorExtraData: String?,
    val registeredBackfillId: Id<DbRegisteredBackfill>
  )

  companion object {
    private val logger = getLogger<CreateBackfillAction>()
  }
}
