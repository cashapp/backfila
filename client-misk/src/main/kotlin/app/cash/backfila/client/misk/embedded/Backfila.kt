package app.cash.backfila.client.misk.embedded

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.internal.parametersToBytes
import kotlin.reflect.KClass
import okio.ByteString

/**
 * Programmatic access to backfila runs. This is useful in tests and development; in production use
 * the Backfila dashboard UI.
 */
interface Backfila {
  fun <Type : Backfill<*, *, *>> createDryRun(
    backfill: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ): BackfillRun<Type>

  fun <Type : Backfill<*, *, *>> createWetRun(
    backfillType: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?
  ): BackfillRun<Type>
}

inline fun <reified Type : Backfill<*, *, *>> Backfila.createDryRun(
  parameters: Any? = null,
  parameterData: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null
): BackfillRun<Type> {
  check(parameterData.isEmpty() || parameters == null) {
    "Only one of parameters and parameterData can be set"
  }
  val parameterBytes =
      if (parameters != null) { parametersToBytes(parameters) } else { parameterData }
  return createDryRun(Type::class, parameterBytes, rangeStart, rangeEnd)
}

inline fun <reified Type : Backfill<*, *, *>> Backfila.createWetRun(
  parameters: Any? = null,
  parameterData: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null
): BackfillRun<Type> {
  check(parameterData.isEmpty() || parameters == null) {
    "Only one of parameters and parameterData can be set"
  }
  val parameterBytes =
      if (parameters != null) { parametersToBytes(parameters) } else { parameterData }
  return createWetRun(Type::class, parameterBytes, rangeStart, rangeEnd)
}
