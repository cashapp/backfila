package app.cash.backfila.client

/**
 * Annotation used on Java Parameters classes to provide null default values. Will only work on
 * Strings or base types.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class BackfilaNullDefault(val name: String)
