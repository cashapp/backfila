package app.cash.backfila.dashboard

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillRunQuery
import app.cash.backfila.service.persistence.BackfillState
import app.cash.backfila.service.persistence.DbBackfillRun
import java.time.Instant
import javax.inject.Inject
import misk.MiskCaller
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.scope.ActionScoped
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class GetActiveBackfillsAction @Inject constructor(
  private val caller: @JvmSuppressWildcards ActionScoped<MiskCaller?>,
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : WebAction {

  @Get("/backfills/currently-running")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Authenticated
  fun currentlyRunningBackfills(): GetCurrentlyRunningBackfillsResponse {
    val backfills = transacter.transaction { session ->
      queryFactory.newQuery<BackfillRunQuery>()
        .state(BackfillState.RUNNING)
        .list(session)
        .sortedBy { it.service.registry_name }
        .map { it.fromDbBackfillRun() }
    }
    return GetCurrentlyRunningBackfillsResponse(backfills)
  }

  data class GetCurrentlyRunningBackfillsResponse(val currentlyRunningBackfillSummaries: List<CurrentlyRunningBackfillSummary>)

  data class CurrentlyRunningBackfillSummary(
    val id: String,
    val name: String,
    val serviceName: String,
    val state: BackfillState,
    val dryRun: Boolean,
    val createdAt: Instant,
    val createdByUser: String?,
    val updatedAt: Instant,
  )

  private fun DbBackfillRun.fromDbBackfillRun(): CurrentlyRunningBackfillSummary =
    CurrentlyRunningBackfillSummary(
      id.toString(),
      registered_backfill.name,
      service.registry_name,
      state,
      dry_run,
      created_at,
      created_by_user,
      updated_at,
    )
}
