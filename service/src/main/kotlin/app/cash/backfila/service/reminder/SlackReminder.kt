package app.cash.backfila.service.reminder

import app.cash.backfila.service.BackfilaConfig
import app.cash.backfila.service.ReminderSettings
import misk.hibernate.Transacter
import misk.slack.SlackClient
import javax.inject.Inject

/**
 * Using usage and setting it will send reminders to remove old unused backfills.
 */
class SlackReminder @Inject constructor(
  val config: BackfilaConfig,
  val reminderSettings: ReminderSettings? = null,
  val slackClient: SlackClient,
  val transacter: Transacter,
){

  fun sendReminders() {

  }
}