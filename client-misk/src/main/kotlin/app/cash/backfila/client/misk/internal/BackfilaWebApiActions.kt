package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.UnknownBackfillException
import app.cash.backfila.client.spi.BackfilaClientServiceHandler
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.GetNextBatchRangeResponse
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillResponse
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.clientservice.RunBatchResponse
import misk.exceptions.BadRequestException
import javax.inject.Inject
import misk.security.authz.Authenticated
import misk.web.AvailableWhenDegraded
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.interceptors.LogRequestResponse
import misk.web.mediatype.MediaTypes

internal class PrepareBackfillAction @Inject constructor(
  private val clientServiceHandler: BackfilaClientServiceHandler,
) : WebAction {
  @Post("/backfila/prepare-backfill")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  @LogRequestResponse(bodySampling = 1.0, errorBodySampling = 1.0)
  @AvailableWhenDegraded
  fun prepareBackfill(@RequestBody request: PrepareBackfillRequest): PrepareBackfillResponse =
    wrapExceptions { clientServiceHandler.prepareBackfill(request) }
}

internal class GetNextBatchRangeAction @Inject constructor(
  private val clientServiceHandler: BackfilaClientServiceHandler,
) : WebAction {
  @Post("/backfila/get-next-batch-range")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  @AvailableWhenDegraded
  fun getNextBatchRange(@RequestBody request: GetNextBatchRangeRequest): GetNextBatchRangeResponse =
    wrapExceptions { clientServiceHandler.getNextBatchRange(request) }
}

internal class RunBatchAction @Inject constructor(
  private val clientServiceHandler: BackfilaClientServiceHandler,
) : WebAction {
  @Post("/backfila/run-batch")
  @RequestContentType(MediaTypes.APPLICATION_PROTOBUF)
  @ResponseContentType(MediaTypes.APPLICATION_PROTOBUF)
  @Authenticated(services = ["backfila"])
  @AvailableWhenDegraded
  fun runBatch(@RequestBody request: RunBatchRequest): RunBatchResponse =
    wrapExceptions { clientServiceHandler.runBatch(request) }
}

private fun <R> wrapExceptions(
  handler: () -> R
): R {
  try {
    return handler.invoke()
  } catch (ex: UnknownBackfillException) {
    throw BadRequestException(cause = ex)
  }
}
