package app.cash.backfila.embedded

import app.cash.backfila.client.Backfill
import javax.inject.Inject
import okio.ByteString

/**
 * Programmatic access to backfila runs for Java. This represents the customer's use of the Backfila
 * UI in tests.
 * Note that passing in Parameters objects for testing is not supported in Java. You must use the
 * parameterData map instead.
 *
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
    rangeEnd: String? = null,
  ): BackfillRun<Type> {
    return backfila.createDryRun(backfill.kotlin, null, parameterData, rangeStart, rangeEnd)
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
    rangeEnd: String? = null,
  ): BackfillRun<Type> {
    return backfila.createWetRun(backfill.kotlin, null, parameterData, rangeStart, rangeEnd)
  }

  val configureServiceData = backfila.configureServiceData
}
