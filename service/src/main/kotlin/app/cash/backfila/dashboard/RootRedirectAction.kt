package app.cash.backfila.dashboard

import java.net.HttpURLConnection
import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.toResponseBody
import okhttp3.Headers

class RootRedirectAction : WebAction {
  @Get("/")
  @Authenticated
  fun root(): Response<ResponseBody> {
    return Response(
        body = "go to /app/".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", "/app/")
    )
  }
}
