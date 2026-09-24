package app.cash.backfila.service.listener

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.FakeSlackClient
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import com.google.inject.Module
import jakarta.inject.Inject
import misk.hibernate.Id
import misk.scope.ActionScope
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.PostMessageResponse
import misk.slack.webapi.helpers.Text
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class SlackHelperTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = BackfilaTestingModule()

  @Inject
  lateinit var configureServiceAction: ConfigureServiceAction

  @Inject
  lateinit var createBackfillAction: CreateBackfillAction

  @Inject
  lateinit var scope: ActionScope

  @Inject
  lateinit var slackHelper: SlackHelper

  @Inject
  lateinit var backfillRunListeners: Set<BackfillRunListener>

  @Inject
  lateinit var slackClient: FakeSlackClient

  @Test
  fun `notifications use started message thread until terminal state`() {
    assertThat(backfillRunListeners.filterIsInstance<SlackHelper>()).containsExactly(slackHelper)
    val id = createBackfill()

    slackHelper.runPaused(id, "molly")
    assertThat(slackClient.postMessageRequests.last().thread_ts).isNull()

    slackClient.postMessageResponse = PostMessageResponse(ok = true, ts = "1234.5678")
    slackHelper.runStarted(id, "molly")
    assertThat(slackClient.postMessageRequests.last().thread_ts).isNull()

    slackHelper.runPaused(id, "molly")
    assertThat(slackClient.postMessageRequests.last().thread_ts).isEqualTo("1234.5678")

    slackHelper.runErrored(id)
    assertThat(slackClient.postMessageRequests.last().thread_ts).isEqualTo("1234.5678")

    slackHelper.runCompleted(id)
    assertThat(slackClient.postMessageRequests.last().thread_ts).isEqualTo("1234.5678")

    slackHelper.runPaused(id, "molly")
    assertThat(slackClient.postMessageRequests.last().thread_ts).isNull()

    slackClient.postMessageResponse = PostMessageResponse(ok = true, ts = "2345.6789")
    slackHelper.runStarted(id, "molly")
    slackHelper.runCancelled(id, "molly")
    assertThat(slackClient.postMessageRequests.last().thread_ts).isEqualTo("2345.6789")

    slackHelper.runErrored(id)
    assertThat(slackClient.postMessageRequests.last().thread_ts).isNull()

    assertThat(slackClient.postMessageRequests).allSatisfy { request ->
      assertThat(request.channel).isEqualTo("#backfila")
      assertThat(request.blocks).hasSize(1)
      assertThat(request.blocks.single()).isInstanceOf(Block::class.java)
      val block = request.blocks.single() as Block
      assertThat(block.type).isEqualTo("section")
      assertThat(block.text?.type).isEqualTo("mrkdwn")
    }
    assertThat(slackClient.postMessageRequests[1].blocks.single()).isEqualTo(
      Block(
        type = "section",
        text = Text(
          type = "mrkdwn",
          text = ":backfila_start::backfila_dryrun: [testing] deep-fryer ($RESERVED_VARIANT) " +
            "`ChickenSandwich` (<backfills/$id|$id>) started by @molly",
        ),
      ),
    )
  }

  private fun createBackfill(): Id<app.cash.backfila.service.persistence.DbBackfillRun> {
    scope.fakeCaller(service = "deep-fryer") {
      configureServiceAction.configureService(
        ConfigureServiceRequest.Builder()
          .backfills(
            listOf(
              ConfigureServiceRequest.BackfillData(
                "ChickenSandwich", "Description", listOf(), null,
                null, false, null, null,
              ),
            ),
          )
          .connector_type(Connectors.ENVOY)
          .slack_channel("#backfila")
          .build(),
      )
    }

    return scope.fakeCaller(user = "molly") {
      Id(
        createBackfillAction.create(
          "deep-fryer",
          RESERVED_VARIANT,
          CreateBackfillRequest.Builder()
            .backfill_name("ChickenSandwich")
            .build(),
        ).backfill_run_id,
      )
    }
  }
}
