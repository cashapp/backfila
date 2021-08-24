package app.cash.backfila.service

import wisp.config.Config
import java.time.DayOfWeek
import java.time.Duration

data class ReminderSettings(
  /** UTC(24) Hour of the day reminders are sent. */
  val utc_hour : Int,
  /** Days of the week reminders are sent. */
  val days_of_week : List<DayOfWeek>,
  /** How long to wait before sending deprecation reminders for backfills that have never been run. */
  val grace_period : Duration,
  /** How long to wait after a backfill has been run before sending deprecation reminders. */
  val idle_period : Duration,
  /** How long to wait between reminders. */
  val reminder_period: Duration
)