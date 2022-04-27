package app.cash.backfila.client

/**
 * Annotation used on Java Parameters classes to correctly name parameters. Will only work on
 * Strings or base types.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class BackfilaRequired(val name: String)
