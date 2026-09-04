package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.ServiceQuery
import app.cash.backfila.ui.pages.ServiceIndexAction
import jakarta.inject.Inject
import java.time.Clock
import misk.MiskCaller
import misk.audit.AuditClient
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.logging.getLogger
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers

class DeleteServiceVariantAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val clock: Clock,
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  private val auditClient: AuditClient,
) : WebAction {
  @Post("/services/{service}/variants/{variant}/delete")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Authenticated(capabilities = ["users"])
  fun delete(
    @PathParam service: String,
    @PathParam variant: String,
  ): Response<ResponseBody> {
    transacter.transaction { session ->
      val dbService = queryFactory.newQuery<ServiceQuery>()
        .registryName(service)
        .variant(variant)
        .notDeleted()
        .uniqueResult(session) ?: throw BadRequestException("`$service`-`$variant` doesn't exist or is already deleted")

      val runningBackfills = queryFactory.newQuery<BackfillRunQuery>()
        .serviceId(dbService.id)
        .state(BackfillState.RUNNING)
        .list(session)

      if (runningBackfills.isNotEmpty()) {
        throw BadRequestException("Cannot delete `$service`-`$variant`: it has ${runningBackfills.size} running backfill(s)")
      }

      dbService.deleted_at = clock.instant()
    }

    val user = caller.get()?.principal ?: ""
    logger.info { "Service variant `$service`-`$variant` soft deleted by `$user`" }
    auditClient.logEvent(
      target = variant,
      description = "Service variant `$service`-`$variant` soft deleted by $user",
      requestorLDAP = user,
      applicationName = service,
    )

    return Response(
      body = "go to ${ServiceIndexAction.PATH}".toResponseBody(),
      statusCode = 303,
      headers = Headers.headersOf("Location", ServiceIndexAction.PATH),
    )
  }

  companion object {
    private val logger = getLogger<DeleteServiceVariantAction>()
  }
}
