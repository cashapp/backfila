package app.cash.backfila.client

/**
 * Annotation used on Java Parameters classes to provide defaults. Will only work on Strings or base
 * types.
 * `value` defaults to an empty string.
 * For a null default set the `nullDefault` field to true. Note that the value will be ignored when setting nullDefault.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class BackfilaDefault(val name: String, val value: String = "", val nullDefault: Boolean = false)
