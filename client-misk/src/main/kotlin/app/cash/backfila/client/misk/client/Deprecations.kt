package app.cash.backfila.client.misk.client

import app.cash.backfila.client.BackfilaHttpClientConfig

// This is for backwards compatibility
// TODO(mikepaw) Remove once the separation between the client and misk is stable.

@Deprecated(
  "Use BackfilaHttpClientConfig in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfilaHttpClientConfig",
    imports = ["app.cash.backfila.client.BackfilaHttpClientConfig"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfilaClientConfig = BackfilaHttpClientConfig

@Deprecated(
  "Use BackfilaMiskClientModule instead.",
  replaceWith = ReplaceWith(
    expression = "BackfilaMiskClientModule",
    imports = ["app.cash.backfila.client.misk.client.BackfilaMiskClientModule"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfilaClientModule = BackfilaMiskClientModule
