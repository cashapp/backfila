package app.cash.backfila.client.misk.dynamodb

import app.cash.backfila.client.dynamodb.DynamoDbBackfill as RealDynamoDbBackfill
import app.cash.backfila.client.dynamodb.DynamoDbBackfillModule as RealDynamoDbBackfillModule
import app.cash.backfila.client.dynamodb.UpdateInPlaceDynamoDbBackfill as RealUpdateInPlaceDynamoDbBackfill

/**
 * client-misk-dynamodb is going away! Add backfila's client-dynamodb as a dependency instead.
 */
@Deprecated(
  "Use DynamoDbBackfill from the client-dynamodb module instead.",
  replaceWith = ReplaceWith(
    expression = "DynamoDbBackfill<I, P>",
    imports = ["app.cash.backfila.client.dynamodb.DynamoDbBackfill"]
  ),
  level = DeprecationLevel.WARNING
)
typealias DynamoDbBackfill<I, P> = RealDynamoDbBackfill<I, P>

/**
 * client-misk-dynamodb is going away! Add backfila's client-dynamodb as a dependency instead.
 */
@Deprecated(
  "Use DynamoDbBackfillModule from the client-dynamodb module instead.",
  replaceWith = ReplaceWith(
    expression = "DynamoDbBackfillModule<T>",
    imports = ["app.cash.backfila.client.static.DynamoDbBackfillModule"]
  ),
  level = DeprecationLevel.WARNING
)
typealias DynamoDbBackfillModule<T> = RealDynamoDbBackfillModule<T>

/**
 * client-misk-dynamodb is going away! Add backfila's client-dynamodb as a dependency instead.
 */
@Deprecated(
  "Use UpdateInPlaceDynamoDbBackfill from the client-dynamodb module instead.",
  replaceWith = ReplaceWith(
    expression = "UpdateInPlaceDynamoDbBackfill<T>",
    imports = ["app.cash.backfila.client.static.DynamoDbBackfillModule"]
  ),
  level = DeprecationLevel.WARNING
)
typealias UpdateInPlaceDynamoDbBackfill<I, P> = RealUpdateInPlaceDynamoDbBackfill<I, P>
