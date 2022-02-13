package app.cash.backfila.client

data class ValidateResult<Param : Any>(
  /**
   * Set to override parameters used for the backfill from the original parameters provided by the user.
   *
   * Example usage: pick a random token to use for the rest of the backfill.
   */
  val parameters: Param,
)
