package app.cash.backfila.client

/**
 * Annotation used on your backfill class and parameters class constructor to send metadata to Backfila.
 */
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val text: String)
