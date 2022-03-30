package app.cash.backfila.client

/**
 * Annotation used on Java Parameters classes to provide defaults. Will only work on Strings or base
 * types.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class BackfilaDefault(val name: String, val value: String)
