package app.cash.backfila.service.listener

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbBackfillRun
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.slack.SlackClient
import wisp.deployment.Deployment

class SlackHelper @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val slackClient: SlackClient,
  private val backfilaConfig: BackfilaConfig,
  private val deployment: Deployment,
) : BackfillRunListener {
  override fun runStarted(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_start:${dryRunEmoji(run)} ${nameAndId(run)} started by @$user"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  override fun runPaused(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_pause:${dryRunEmoji(run)} ${nameAndId(run)} paused by @$user"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  override fun runErrored(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_error:${dryRunEmoji(run)} ${nameAndId(run)} paused due to error"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  override fun runCompleted(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_complete:${dryRunEmoji(run)} ${nameAndId(run)} completed"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  override fun runCancelled(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_cancel:${dryRunEmoji(run)} ${nameAndId(run)} canceled by @$user"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
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
