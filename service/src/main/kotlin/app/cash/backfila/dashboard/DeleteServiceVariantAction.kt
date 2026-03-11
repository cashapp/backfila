package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.ServiceQuery
import java.time.Clock
import javax.inject.Inject
import misk.MiskCaller
import misk.exceptions.BadRequestException
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.PathParam
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class DeleteServiceVariantAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val clock: Clock,
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
) : WebAction {
  @Post("/services/{service}/variants/{variant}/delete")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated(capabilities = ["users"])
  fun delete(
    @PathParam service: String,
    @PathParam variant: String,
  ) {
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

      dbService.softDelete(clock)
    }
  }
}
