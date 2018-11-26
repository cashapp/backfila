package com.squareup.backfila.actions

import misk.security.authz.Unauthenticated
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class PingAction : WebAction {
  data class Ping(val message: String)

  @Post("/ping")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @Unauthenticated
  fun ping(@RequestBody ping: Ping) = Ping("pong ${ping.message}")

}