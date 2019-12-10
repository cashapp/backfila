package app.cash.backfila.client.misk.embedded

import app.cash.backfila.client.misk.Backfill
import okio.ByteString
import kotlin.reflect.KClass

/**
 * Programmatic access to backfila runs. This is useful in tests and development; in production use
 * the Backfila dashboard UI.
 */
interface Backfila {
  fun <Type : Backfill<*, *>> createDryRun(
    backfill: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ): BackfillRun<Type>

  fun <Type : Backfill<*, *>> createWetRun(
    backfillType: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ): BackfillRun<Type>
}

inline fun <reified Type : Backfill<*, *>> Backfila.createDryRun(
  parameters: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null
): BackfillRun<Type> = createDryRun(Type::class, parameters, rangeStart, rangeEnd)

inline fun <reified Type : Backfill<*, *>> Backfila.createWetRun(
  parameters: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null
): BackfillRun<Type> = createWetRun(Type::class, parameters, rangeStart, rangeEnd)
