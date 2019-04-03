package com.squareup.backfila.dashboard

import com.squareup.backfila.service.BackfilaDb
import com.squareup.backfila.service.RegisteredBackfillQuery
import com.squareup.backfila.service.ServiceQuery
import misk.MiskCaller
import misk.exceptions.BadRequestException
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
import okhttp3.Headers
import java.net.HttpURLConnection
import javax.inject.Inject

class CreateBackfillRunAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : WebAction {

  data class CreateBackfillRequest(val backfill_name: String)

  @Post("/{service}/create")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  // TODO which roles?
  @Authenticated(roles = ["eng"])
  fun create(
    @PathParam service: String,
    @RequestBody request: CreateBackfillRequest
  ) : Response<ResponseBody> {
    logger.info { "Create backfill for $service by ${caller.get()?.user}" }

    transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
          .registryName(service)
          .uniqueResult(session) ?: throw BadRequestException("$service doesn't exist")

      // Add any missing backfills, update existing ones, and mark missing ones as deleted.
      val registeredBackfill = queryFactory.newQuery<RegisteredBackfillQuery>()
          .serviceId(dbService.id)
          .name(request.backfill_name)
          .active()
          .uniqueResult(session) ?: throw BadRequestException("${request.backfill_name} doesn't exist")
      logger.info { "Found registered backfill existing backfills for `$service`::`${request.backfill_name}` ${registeredBackfill.id}" }
    }
    val id = 123
    return Response(
        body = "go to /backfills/$id".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.of("Location", "/backfills/$id"))
  }

  companion object {
    private val logger = getLogger<CreateBackfillRunAction>()
  }
}
