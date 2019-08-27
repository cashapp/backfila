package com.squareup.backfila.service

import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.slack.SlackClient
import javax.inject.Inject

class SlackHelper @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val slackClient: SlackClient
) {
  // TODO clickable links to the status page, need base url in BackfilaConfig
  fun runStarted(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      "`${run.registered_backfill.name}` started by @$user" to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  fun runPaused(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      "`${run.registered_backfill.name}` paused by @$user" to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  fun runErrored(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      "`${run.registered_backfill.name}` paused due to error" to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  fun runCompleted(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      "`${run.registered_backfill.name}` completed" to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }
}