package app.cash.backfila.client

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC

/**
 * Annotation used on your backfill class to indicate that this backfill should continue to be
 * useful until the specified date. Most backfills should not specify anything which defaults to as
 * soon as possible. No removal reminders will occur until after this date.
 *
 * deleteBy : Must have a format of YYYY-MM-DD.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DeleteBy(val deleteBy: String)

fun DeleteBy.parseDeleteByDate(): Instant {
  return LocalDate.parse(this.deleteBy).atStartOfDay().toInstant(UTC)
}
