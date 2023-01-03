package app.cash.backfila.client.misk.static

import app.cash.backfila.client.stat.StaticDatasourceBackfill as RealStaticDataSourceBackfill
import app.cash.backfila.client.stat.StaticDatasourceBackfillModule as RealStaticDatasourceBackfillModule

/**
 * client-misk-static is going away! Add backfila's client-static as a dependency instead.
 */
@Deprecated(
  "Use StaticDatasourceBackfill from the client-static module instead.",
  replaceWith = ReplaceWith(
    expression = "StaticDatasourceBackfill<I, P>",
    imports = ["app.cash.backfila.client.stat.StaticDatasourceBackfill"],
  ),
  level = DeprecationLevel.WARNING,
)
typealias StaticDatasourceBackfill<I, P> = RealStaticDataSourceBackfill<I, P>

/**
 * client-misk-static is going away! Add backfila's client-static as a dependency instead.
 */
@Deprecated(
  "Use StaticDatasourceBackfillModule from the client-static module instead.",
  replaceWith = ReplaceWith(
    expression = "StaticDatasourceBackfillModule<T>",
    imports = ["app.cash.backfila.client.stat.StaticDatasourceBackfillModule"],
  ),
  level = DeprecationLevel.WARNING,
)
typealias StaticDatasourceBackfillModule<T> = RealStaticDatasourceBackfillModule<T>
