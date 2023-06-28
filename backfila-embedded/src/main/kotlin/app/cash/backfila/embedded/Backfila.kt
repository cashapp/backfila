package app.cash.backfila.embedded

import app.cash.backfila.client.Backfill
import app.cash.backfila.client.spi.parametersToBytes
import app.cash.backfila.protos.service.ConfigureServiceRequest
import kotlin.reflect.KClass
import okio.ByteString

/**
 * Programmatic access to backfila UI features. This is useful in tests and development; in
 * production use the Backfila dashboard UI.
 * Java customers should use [JavaBackfila].
 */
interface Backfila {
  fun <Type : Backfill> createDryRun(
    backfill: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?,
  ): BackfillRun<Type>

  fun <Type : Backfill> createWetRun(
    backfillType: KClass<Type>,
    parameters: Map<String, ByteString>,
    rangeStart: String?,
    rangeEnd: String?,
  ): BackfillRun<Type>

  fun <Type : Backfill> findExistingRun(
    backfillType: KClass<Type>,
    backfillRunId: Long,
  ): BackfillRun<Type>

  val configureServiceData: ConfigureServiceRequest?
}

inline fun <reified Type : Backfill> Backfila.createDryRun(
  parameters: Any? = null,
  parameterData: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null,
): BackfillRun<Type> {
  return createDryRun(Type::class, parameters, parameterData, rangeStart, rangeEnd)
}

fun <Type : Backfill> Backfila.createDryRun(
  backfill: KClass<Type>,
  parameters: Any?,
  parameterData: Map<String, ByteString>,
  rangeStart: String?,
  rangeEnd: String?,
): BackfillRun<Type> {
  check(parameterData.isEmpty() || parameters == null) {
    "Only one of parameters and parameterData can be set"
  }
  val parameterBytes =
    if (parameters != null) {
      parametersToBytes(parameters)
    } else {
      parameterData
    }
  return createDryRun(backfill, parameterBytes, rangeStart, rangeEnd)
}

inline fun <reified Type : Backfill> Backfila.createWetRun(
  parameters: Any? = null,
  parameterData: Map<String, ByteString> = mapOf(),
  rangeStart: String? = null,
  rangeEnd: String? = null,
): BackfillRun<Type> {
  return createWetRun(Type::class, parameters, parameterData, rangeStart, rangeEnd)
}

fun <Type : Backfill> Backfila.createWetRun(
  backfill: KClass<Type>,
  parameters: Any?,
  parameterData: Map<String, ByteString>,
  rangeStart: String?,
  rangeEnd: String?,
): BackfillRun<Type> {
  check(parameterData.isEmpty() || parameters == null) {
    "Only one of parameters and parameterData can be set"
  }
  val parameterBytes =
    if (parameters != null) {
      parametersToBytes(parameters)
    } else {
      parameterData
    }
  return createWetRun(backfill, parameterBytes, rangeStart, rangeEnd)
}
