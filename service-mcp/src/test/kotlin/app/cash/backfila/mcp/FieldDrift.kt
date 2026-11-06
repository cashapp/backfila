package app.cash.backfila.mcp

import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import org.assertj.core.api.Assertions.assertThat

/**
 * Fails when [source] has a property that [output] doesn't have under the same name, and that isn't
 * listed in [renamed] or [omitted]. Tools map Backfila's types by hand, so this catches a field added
 * to Backfila that a tool would otherwise silently drop.
 */
internal fun assertMapsEveryField(
  source: KClass<*>,
  output: KClass<*>,
  renamed: Map<String, String> = mapOf(),
  omitted: Set<String> = setOf(),
) {
  val sourceFields = source.memberProperties.map { it.name }.toSet()
  val outputFields = output.memberProperties.map { it.name }.toSet()

  assertThat(renamed.keys + omitted)
    .describedAs("renamed or omitted fields that ${source.simpleName} doesn't have")
    .isSubsetOf(sourceFields)
  assertThat(renamed.values)
    .describedAs("rename targets that ${output.simpleName} doesn't have")
    .isSubsetOf(outputFields)
  assertThat(sourceFields - renamed.keys - omitted)
    .describedAs("fields of ${source.simpleName} that ${output.simpleName} drops; map them, or list them as renamed or omitted")
    .isSubsetOf(outputFields)
}
