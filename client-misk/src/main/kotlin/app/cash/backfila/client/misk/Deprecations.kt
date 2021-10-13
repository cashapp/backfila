package app.cash.backfila.client.misk

import app.cash.backfila.client.BackfilaManagementClient
import app.cash.backfila.client.Backfill
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.NoParameters

// This is for backwards compatibility
// TODO(mikepaw) Remove once the separation between the client and misk is stable.

@Deprecated(
  "Use Backfill in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "Backfill",
    imports = ["app.cash.backfila.client.Backfill"]
  ),
  level = DeprecationLevel.ERROR
)
typealias Backfill = Backfill

@Deprecated(
  "Use BackfillConfig in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfillConfig<Param>",
    imports = ["app.cash.backfila.client.BackfillConfig"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfillConfig<Param> = BackfillConfig<Param>

@Deprecated(
  "Use Description in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "Description",
    imports = ["app.cash.backfila.client.Description"]
  ),
  level = DeprecationLevel.ERROR
)
typealias Description = Description

@Deprecated(
  "Use NoParameters in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "NoParameters",
    imports = ["app.cash.backfila.client.NoParameters"]
  ),
  level = DeprecationLevel.ERROR
)
typealias NoParameters = NoParameters

@Deprecated(
  "Use BackfilaManagementClient in the client module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfilaManagementClient",
    imports = ["app.cash.backfila.client.BackfilaManagementClient"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfilaManagementClient = BackfilaManagementClient

@Deprecated(
  "Use MiskBackfillModule instead.",
  replaceWith = ReplaceWith(
    expression = "MiskBackfillModule",
    imports = ["app.cash.backfila.client.misk.MiskBackfillModule"]
  ),
  level = DeprecationLevel.ERROR
)
typealias BackfillModule = MiskBackfillModule
