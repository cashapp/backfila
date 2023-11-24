package app.cash.backfila.development.mcdees

import app.cash.backfila.development.DevServiceConstants

/**
 * The American hamburger chain.
 */
fun main(args: Array<String>) {
  McDeesDevelopmentServiceBase().runMcDees(
    variant = "USA",
    port = DevServiceConstants.MC_DEES_USA_PORT,
    args,
  )
}
