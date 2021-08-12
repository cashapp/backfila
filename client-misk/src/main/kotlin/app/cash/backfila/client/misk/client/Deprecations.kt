package app.cash.backfila.client.misk.client

import app.cash.backfila.client.config.BackfilaClientConfig

// This is for backwards compatibility
// TODO(mikepaw) Remove once the separation between the client and misk is stable.

@Deprecated(
  "Use BackfillaClientConfig in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfillaClientConfig",
    imports = ["app.cash.backfila.client.config.BackfilaClientConfig"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfillaClientConfig = BackfilaClientConfig

@Deprecated(
  "Use BackfilaMiskClientModule instead.",
  replaceWith = ReplaceWith(
    expression = "BackfilaMiskClientModule",
    imports = ["app.cash.backfila.client.misk.client.BackfilaMiskClientModule"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfilaClientModule = BackfilaMiskClientModule
