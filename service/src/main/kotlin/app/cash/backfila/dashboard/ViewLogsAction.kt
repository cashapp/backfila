package app.cash.backfila.dashboard

import app.cash.backfila.service.BackfilaDb
import app.cash.backfila.service.DbBackfillRun
import java.net.HttpURLConnection
import javax.inject.Inject
import misk.exceptions.BadRequestException
import misk.hibernate.Id
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.loadOrNull
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.toResponseBody
import okhttp3.Headers

interface ViewLogsUrlProvider {
  fun getUrl(session: Session, backfillRun: DbBackfillRun): String
}

class ViewLogsAction @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val viewLogsUrlProvider: ViewLogsUrlProvider
) : WebAction {
  @Get("/backfills/{id}/view-logs")
  @Authenticated
  fun viewLogs(
    @PathParam id: Long
  ): Response<ResponseBody> {
    val url = transacter.transaction { session ->
      val backfillRun = session.loadOrNull<DbBackfillRun>(Id(id))
          ?: throw BadRequestException("backfill $id doesn't exist")
      viewLogsUrlProvider.getUrl(session, backfillRun)
    }
    return Response(
        body = "go to $url".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", url)
    )
  }
}
