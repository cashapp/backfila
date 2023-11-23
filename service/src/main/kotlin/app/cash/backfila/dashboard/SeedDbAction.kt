package app.cash.backfila.dashboard

import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.CreateAndStartBackfillAction
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateAndStartBackfillRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import javax.inject.Inject
import misk.MiskCaller
import misk.inject.keyOf
import misk.scope.ActionScope
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes

class SeedDbAction @Inject constructor(
  val configureServiceAction: ConfigureServiceAction,
  val createAndStartBackfillAction: CreateAndStartBackfillAction,
  val scope: ActionScope,
) : WebAction {

  @Get("/seed")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun seed(): String {
    scope.close()
    createServiceWithBackfills("deep-fryer", listOf("ChickenSandwich"))
    startBackfill("deep-fryer", "ChickenSandwich")

    for (i in 1..100) {
      createServiceWithBackfills("deep-fryer-$i", listOf("ChickenSandwich"))
    }

    return "yo"
  }

  private fun startBackfill(serviceName: String, backfillName: String) {
    scope.fakeCaller(service = serviceName) {
      val response = createAndStartBackfillAction.createAndStartBackfill(
        CreateAndStartBackfillRequest.Builder()
          .create_request(
            CreateBackfillRequest.Builder()
              .backfill_name(backfillName)
              .build(),
          )
          .build(),
      )
    }
  }

  private fun createServiceWithBackfills(serviceName: String, backfillNames: List<String>) {
    scope.fakeCaller(service = serviceName) {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            backfillNames.map { backfillName ->
              ConfigureServiceRequest.BackfillData(
                backfillName, "Description", listOf(), null,
                null, false, null,
              )
            },
          )
          .connector_type("DEV")
          .build(),
      )
    }
  }

  fun <T> ActionScope.fakeCaller(
    service: String? = null,
    user: String? = null,
    function: () -> T,
  ): T {
    return enter(mapOf(keyOf<MiskCaller>() to MiskCaller(service = service, user = user)))
      .use { function() }
  }
}
