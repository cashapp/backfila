package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaClientConfig

// This is for backwards compatibility
// TODO(mikepaw) Remove once the separation between the client and misk is stable.

@Deprecated(
  "Use BackfilaClientConfig in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfilaClientConfig",
    imports = ["app.cash.backfila.client.BackfilaClientConfig"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfilaClientConfig = BackfilaClientConfig

@Deprecated(
  "Use BackfilaMiskClientModule instead.",
  replaceWith = ReplaceWith(
    expression = "BackfilaMiskClientModule",
    imports = ["app.cash.backfila.client.misk.client.BackfilaMiskClientModule"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfilaClientModule = BackfilaMiskClientModule
