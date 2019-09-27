package app.cash.backfila.client.misk

import com.google.inject.Injector
import misk.security.authz.Authenticated
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject

internal class BackfilaClassResolver @Inject constructor(
  private val injector: Injector,
  private val backfills: Map<String, Class<out Backfill>>
) {

}

internal class PrepareBackfillAction @Inject constructor(
//  private val backfilaClassResolver: BackfilaClassResolver
) : WebAction {
  @Post("/backfila/prepare-backfill")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  fun prepareBackfill() {
    // TODO
  }
}

class GetNextBatchRangeAction : WebAction {
  @Post("/backfila/get-next-batch-range")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  fun getNextBatchRange() {
    // TODO
  }
}

class RunBatchAction : WebAction {
  @Post("/backfila/run-batch")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  fun runBatch() {
    // TODO
  }
}