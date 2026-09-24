package app.cash.backfila.service.listener

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbBackfillRun
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.slack.SlackClient as SlackWebhookClient
import misk.slack.webapi.SlackClient
import misk.slack.webapi.helpers.Block
import misk.slack.webapi.helpers.PostMessageRequest
import misk.slack.webapi.helpers.Text
import wisp.deployment.Deployment

@Singleton
class SlackHelper @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val slackClient: SlackClient,
  private val slackWebhookClient: SlackWebhookClient,
  private val backfilaConfig: BackfilaConfig,
  private val deployment: Deployment,
) : BackfillRunListener {
  private val threadTimestamps = ConcurrentHashMap<Id<DbBackfillRun>, String>()

  override fun runStarted(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_start:${dryRunEmoji(run)} ${nameAndId(run)} started by @$user"
      message to run.service.slack_channel
    }
    postMessage(message, channel)?.let { threadTimestamps[id] = it }
  }

  override fun runPaused(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_pause:${dryRunEmoji(run)} ${nameAndId(run)} paused by @$user"
      message to run.service.slack_channel
    }
    postMessage(message, channel, threadTimestamps[id])
  }

  override fun runErrored(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_error:${dryRunEmoji(run)} ${nameAndId(run)} paused due to error"
      message to run.service.slack_channel
    }
    postMessage(message, channel, threadTimestamps[id])
  }

  override fun runCompleted(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_complete:${dryRunEmoji(run)} ${nameAndId(run)} completed"
      message to run.service.slack_channel
    }
    postMessage(message, channel, threadTimestamps.remove(id))
  }

  override fun runCancelled(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_cancel:${dryRunEmoji(run)} ${nameAndId(run)} canceled by @$user"
      message to run.service.slack_channel
    }
    postMessage(message, channel, threadTimestamps.remove(id))
  }

  private fun postMessage(message: String, channel: String?, threadTimestamp: String? = null): String? {
    if (backfilaConfig.slack?.api == null) {
      slackWebhookClient.postMessage("Backfila", ":backfila:", message, channel)
      return null
    }

    val destination = channel ?: backfilaConfig.slack?.default_channel ?: return null

    return slackClient.postMessage(
      PostMessageRequest(
        channel = destination,
        blocks = listOf(
          Block(
            type = "section",
            text = Text(type = "mrkdwn", text = message),
          ),
        ),
        thread_ts = threadTimestamp,
      ),
    ).ts
  }

  private fun nameAndId(run: DbBackfillRun) =
    "[${deployment.name}] ${run.service.registry_name} (${run.service.variant}) `${run.registered_backfill.name}` " +
      "(${idLink(run.id)})"

  private fun dryRunEmoji(run: DbBackfillRun) =
    if (run.dry_run) {
      ":backfila_dryrun:"
    } else {
      ":backfila_wetrun:"
    }

  private fun idLink(id: Id<DbBackfillRun>): String {
    val url = "${backfilaConfig.web_url_root}backfills/$id"
    return "<$url|$id>"
  }
}
