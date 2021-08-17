package app.cash.backfila.client.misk.embedded

import app.cash.backfila.client.Backfill
import okio.ByteString

// This is for backwards compatibility
// TODO(mikepaw) Remove once the separation between the client and misk is stable.

/**
 * You will also need to import a new dependency probably only for tests to the embedded service
 * testImplementation(project(":backfila-embedded"))
 */
@Deprecated(
  "Use Backfila in the backfila-embedded module instead.",
  replaceWith = ReplaceWith(
    expression = "Backfila",
    imports = ["app.cash.backfila.embedded.Backfila"]
  ),
  level = DeprecationLevel.WARNING
)
class Backfila

/**
 * You will also need to import a new dependency probably only for tests to the embedded service
 * testImplementation(project(":backfila-embedded"))
 */
@Deprecated(
  "Use BackfilaRun in the backfila-embedded module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfillRun<B>",
    imports = ["app.cash.backfila.embedded.BackfillRun"]
  ),
  level = DeprecationLevel.WARNING
)
interface BackfillRun<B : Backfill>

/**
 * You will also need to import a new dependency probably only for tests to the embedded service
 * testImplementation(project(":backfila-embedded"))
 */
@Deprecated(
  "Use EmbeddedBackfilaModule in the backfila-embedded module instead.",
  replaceWith = ReplaceWith(
    expression = "EmbeddedBackfilaModule",
    imports = ["app.cash.backfila.embedded.EmbeddedBackfilaModule"]
  ),
  level = DeprecationLevel.ERROR
)
class EmbeddedBackfilaModule

/**
 * You will also need to import a new dependency probably only for tests to the embedded service
 * testImplementation(project(":backfila-embedded"))
 */
@Deprecated(
  "Use createDryRun in the backfila-embedded module instead.",
  replaceWith = ReplaceWith(
    expression = "createDryRun",
    imports = ["app.cash.backfila.embedded.createDryRun"]
  ),
  level = DeprecationLevel.ERROR
)
inline fun <reified Type : Backfill> Backfila.createDryRun(
  parameters: Any? = null,
  parameterData: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null
): BackfillRun<Type> = error("deprecated!")

/**
 * You will also need to import a new dependency probably only for tests to the embedded service
 * testImplementation(project(":backfila-embedded"))
 */
@Deprecated(
  "Use createWetRun in the backfila-embedded module instead.",
  replaceWith = ReplaceWith(
    expression = "createWetRun",
    imports = ["app.cash.backfila.embedded.createWetRun"]
  ),
  level = DeprecationLevel.ERROR
)
inline fun <reified Type : Backfill> Backfila.createWetRun(
  parameters: Any? = null,
  parameterData: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null
): BackfillRun<Type> = error("deprecated!")
