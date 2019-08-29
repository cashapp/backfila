package com.squareup.backfila.dashboard

import misk.security.authz.Authenticated
import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.toResponseBody
import okhttp3.Headers
import java.net.HttpURLConnection

class RootRedirectAction : WebAction {
  @Get("/")
  @Authenticated
  fun root(): Response<ResponseBody> {
    return Response(
        body = "go to /app/home/".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = Headers.headersOf("Location", "/app/home/")
    )
  }
}
