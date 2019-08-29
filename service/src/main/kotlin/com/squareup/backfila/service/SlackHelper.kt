package com.squareup.backfila.service

import misk.hibernate.Id
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.slack.SlackClient
import javax.inject.Inject

class SlackHelper @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val slackClient: SlackClient,
  private val backfilaConfig: BackfilaConfig
) {
  fun runStarted(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_start: `${run.registered_backfill.name}` (${idLink(id)}) started by @$user"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  fun runPaused(id: Id<DbBackfillRun>, user: String) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_pause: `${run.registered_backfill.name}` (${idLink(id)}) paused by @$user"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  fun runErrored(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_error: `${run.registered_backfill.name}` (${idLink(id)}) paused due to error"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  fun runCompleted(id: Id<DbBackfillRun>) {
    val (message, channel) = transacter.transaction { session ->
      val run = session.load(id)
      val message = ":backfila_complete: `${run.registered_backfill.name}` (${idLink(id)}) completed"
      message to run.service.slack_channel
    }
    slackClient.postMessage("Backfila", ":backfila:", message, channel)
  }

  private fun idLink(id: Id<DbBackfillRun>): String {
    val url = "${backfilaConfig.web_url_root}backfills/$id"
    return "<$url|$id>"
  }
}