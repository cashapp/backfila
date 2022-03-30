package app.cash.backfila.embedded

import app.cash.backfila.client.Backfill
import okio.ByteString
import javax.inject.Inject

/**
 * Programmatic access to backfila runs for Java. This represents the customer's use of the Backfila
 * UI in tests.
 * Kotlin customers should use [Backfila] directly.
 */
class JavaBackfila @Inject constructor(private val backfila: Backfila) {

  // Dry Run Signatures
  fun <Type : Backfill> createDryRun(
    backfill: Class<Type>,
  ): BackfillRun<Type> {
    return backfila.createDryRun(backfill.kotlin, null, mapOf(), null, null)
  }

  @JvmOverloads
  fun <Type : Backfill> createDryRun(
    backfill: Class<Type>,
    parameterData: Map<String, ByteString>,
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<Type> {
    return backfila.createDryRun(backfill.kotlin, null, parameterData, rangeStart, rangeEnd)
  }

  @JvmOverloads
  fun <Type : Backfill> createDryRun(
    backfill: Class<Type>,
    parameters: Any?,
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<Type> {
    return backfila.createDryRun(backfill.kotlin, parameters, mapOf(), rangeStart, rangeEnd)
  }

  // Wet Run Signatures
  fun <Type : Backfill> createWetRun(
    backfill: Class<Type>,
  ): BackfillRun<Type> {
    return backfila.createWetRun(backfill.kotlin, null, mapOf(), null, null)
  }

  @JvmOverloads
  fun <Type : Backfill> createWetRun(
    backfill: Class<Type>,
    parameterData: Map<String, ByteString>,
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<Type> {
    return backfila.createWetRun(backfill.kotlin, null, parameterData, rangeStart, rangeEnd)
  }

  @JvmOverloads
  fun <Type : Backfill> createWetRun(
    backfill: Class<Type>,
    parameters: Any?,
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<Type> {
    return backfila.createWetRun(backfill.kotlin, parameters, mapOf(), rangeStart, rangeEnd)
  }
}
