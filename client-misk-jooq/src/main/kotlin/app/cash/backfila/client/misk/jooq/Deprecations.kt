package app.cash.backfila.client.misk.jooq

import app.cash.backfila.client.jooq.BackfillBatch as RealBackfillBatch
import app.cash.backfila.client.jooq.BackfillJooqTransacter as RealBackfillJooqTransacter
import app.cash.backfila.client.jooq.ByteStringSerializer as RealByteStringSerializer
import app.cash.backfila.client.jooq.CompoundKeyComparer as RealCompoundKeyComparer
import app.cash.backfila.client.jooq.CompoundKeyComparisonOperator as RealCompoundKeyComparisonOperator
import app.cash.backfila.client.jooq.JooqBackfill as RealJooqBackfill
import app.cash.backfila.client.jooq.JooqBackfillModule as RealJooqBackfillModule

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use BackfillBatch from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfillBatch<K, Param>",
    imports = ["app.cash.backfila.client.jooq.BackfillBatch"]
  ),
  level = DeprecationLevel.WARNING
)
typealias BackfillBatch<K, Param> = RealBackfillBatch<K, Param>

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use BackfillJooqTransacter from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "BackfillJooqTransacter",
    imports = ["app.cash.backfila.client.jooq.BackfillJooqTransacter"]
  ),
  level = DeprecationLevel.WARNING
)
typealias BackfillJooqTransacter = RealBackfillJooqTransacter

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use ByteStringSerializer from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "ByteStringSerializer<T>",
    imports = ["app.cash.backfila.client.jooq.ByteStringSerializer"]
  ),
  level = DeprecationLevel.WARNING
)
typealias ByteStringSerializer<T> = RealByteStringSerializer<T>

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use CompoundKeyComparer from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "CompoundKeyComparer<T>",
    imports = ["app.cash.backfila.client.jooq.CompoundKeyComparer"]
  ),
  level = DeprecationLevel.WARNING
)
typealias CompoundKeyComparer<T> = RealCompoundKeyComparer<T>

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use CompoundKeyComparisonOperator from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "CompoundKeyComparisonOperator<T>",
    imports = ["app.cash.backfila.client.jooq.CompoundKeyComparisonOperator"]
  ),
  level = DeprecationLevel.WARNING
)
typealias CompoundKeyComparisonOperator<T> = RealCompoundKeyComparisonOperator<T>

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use JooqBackfill from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "JooqBackfill<K, Param>",
    imports = ["app.cash.backfila.client.jooq.JooqBackfill"]
  ),
  level = DeprecationLevel.WARNING
)
typealias JooqBackfill<K, Param> = RealJooqBackfill<K, Param>

/**
 * client-misk-jooq is going away! Add backfila's client-jooq as a dependency instead.
 */
@Deprecated(
  "Use JooqBackfillModule from the client-jooq module instead.",
  replaceWith = ReplaceWith(
    expression = "JooqBackfillModule<T>",
    imports = ["app.cash.backfila.client.jooq.JooqBackfillModule"]
  ),
  level = DeprecationLevel.WARNING
)
typealias JooqBackfillModule<T> = RealJooqBackfillModule<T>
