package app.cash.backfila.client.misk

import okio.ByteString

data class BackfillConfig(
  /**
   * Populated from class level @Parameter annotations
   * Use parameter function to get just the value (as a String)
   * @see com.squareup.backfila.client.Parameter
   */
  val parameters: Map<String, ByteString>,
  val dryRun: Boolean = true
) {
  fun parameter(name: String): String? {
    return parameters[name]?.utf8()
  }
}
