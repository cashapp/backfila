package app.cash.backfila.development.mcdees

import app.cash.backfila.development.DevServiceConstants

/**
 * The Canadian hamburger chain.
 */
fun main(args: Array<String>) {
  McDeesDevelopmentServiceBase().runMcDees(
    variant = "CANADA",
    port = DevServiceConstants.MC_DEES_CANADA_PORT,
    args,
  )
}
