package app.cash.backfila.client.spi

/**
 * Service provider interface for backends like Hibernate and DynamoDb. Backends construct operators
 * that actually run the backfill.
 */
interface BackfillBackend {
  fun create(backfillName: String, backfillId: String): BackfillOperator?
  fun backfills(): Set<BackfillRegistration>
}
