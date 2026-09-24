package app.cash.backfila.service.listener

import app.cash.backfila.BackfilaTestingModule
import app.cash.backfila.FakeSlackClient
import app.cash.backfila.FakeSlackWebhookClient
import app.cash.backfila.api.ConfigureServiceAction
import app.cash.backfila.api.ConfigureServiceAction.Companion.RESERVED_VARIANT
import app.cash.backfila.client.Connectors
import app.cash.backfila.dashboard.CreateBackfillAction
import app.cash.backfila.fakeCaller
import app.cash.backfila.protos.service.ConfigureServiceRequest
import app.cash.backfila.protos.service.CreateBackfillRequest
import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.BackfilaSlackConfig
import app.cash.backfila.service.persistence.BackfilaDb
import com.google.inject.Module
import jakarta.inject.Inject
import misk.config.Secret
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.scope.ActionScope
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.PostMessageResponse
import misk.slack.webapi.helpers.Text
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.Deployment

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

  @Inject
  lateinit var slackWebhookClient: FakeSlackWebhookClient

  @Inject
  lateinit var backfilaConfig: BackfilaConfig

  @Inject
  lateinit var deployment: Deployment

  @Inject
  @BackfilaDb
  lateinit var transacter: Transacter

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

  @Test
  fun `legacy Slack config continues posting through webhook`() {
    val id = createBackfill(slackChannel = null)
    val testSecret = object : Secret<String> {
      override val value = "unused"
    }
    val legacyConfig = backfilaConfig.copy(
      slack = BackfilaSlackConfig(webhook_path = testSecret, default_channel = "#default"),
    )
    val legacyHelper = SlackHelper(transacter, slackClient, slackWebhookClient, legacyConfig, deployment)

    legacyHelper.runStarted(id, "molly")
    legacyHelper.runPaused(id, "molly")
    legacyHelper.runErrored(id)
    legacyHelper.runCompleted(id)
    legacyHelper.runCancelled(id, "molly")

    assertThat(slackClient.postMessageRequests).isEmpty()
    assertThat(slackWebhookClient.postMessageRequests).hasSize(5)
    assertThat(slackWebhookClient.postMessageRequests.map { it.username }).containsOnly("Backfila")
    assertThat(slackWebhookClient.postMessageRequests.map { it.iconEmoji }).containsOnly(":backfila:")
    assertThat(slackWebhookClient.postMessageRequests.map { it.channel }).containsOnlyNulls()
    assertThat(slackWebhookClient.postMessageRequests.map { it.message }).allSatisfy { message ->
      assertThat(message).contains("ChickenSandwich")
    }
    assertThat(slackWebhookClient.postMessageRequests[0].message).contains("started by @molly")
    assertThat(slackWebhookClient.postMessageRequests[1].message).contains("paused by @molly")
    assertThat(slackWebhookClient.postMessageRequests[2].message).contains("paused due to error")
    assertThat(slackWebhookClient.postMessageRequests[3].message).contains("completed")
    assertThat(slackWebhookClient.postMessageRequests[4].message).contains("canceled by @molly")
  }

  @Test
  fun `web API uses legacy default channel when service has no channel`() {
    val id = createBackfill(slackChannel = null)
    val testSecret = object : Secret<String> {
      override val value = "unused"
    }
    val configWithDefaultChannel = backfilaConfig.copy(
      slack = backfilaConfig.slack!!.copy(webhook_path = testSecret, default_channel = "#default"),
    )
    val helper = SlackHelper(transacter, slackClient, slackWebhookClient, configWithDefaultChannel, deployment)
    slackClient.postMessageResponse = PostMessageResponse(ok = true, ts = "1234.5678")

    helper.runStarted(id, "molly")
    helper.runCompleted(id)

    assertThat(slackClient.postMessageRequests.map { it.channel }).containsOnly("#default")
    assertThat(slackClient.postMessageRequests[1].thread_ts).isEqualTo("1234.5678")
    assertThat(slackWebhookClient.postMessageRequests).isEmpty()
  }

  private fun createBackfill(slackChannel: String? = "#backfila"): Id<app.cash.backfila.service.persistence.DbBackfillRun> {
    scope.fakeCaller(service = "deep-fryer") {
      val request = ConfigureServiceRequest.Builder()
        .backfills(
          listOf(
            ConfigureServiceRequest.BackfillData(
              "ChickenSandwich", "Description", listOf(), null,
              null, false, null, null,
            ),
          ),
        )
        .connector_type(Connectors.ENVOY)
      if (slackChannel != null) request.slack_channel(slackChannel)
      configureServiceAction.configureService(request.build())
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
