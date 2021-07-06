package app.cash.backfila.client.misk

/**
 * Annotation used on your backfill class to indicate that this backfill will continue to be used in
 * the future. Most backfills should probably be deprecated after a period of time. This annotation
 * highlights the exceptions.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LongTerm
