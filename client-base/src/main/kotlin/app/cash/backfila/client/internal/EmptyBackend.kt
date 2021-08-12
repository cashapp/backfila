package app.cash.backfila.client.internal

import app.cash.backfila.client.spi.BackfillBackend
import app.cash.backfila.client.spi.BackfillOperator
import app.cash.backfila.client.spi.BackfillRegistration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Basic backend that registers no backfills. Allows for Backfila to be setup but with no registered
 * backfills. This will encourage people to remove old backfills even if it is the last one.
 */
@Singleton
class EmptyBackend @Inject constructor() : BackfillBackend {
  override fun create(backfillName: String, backfillId: String): BackfillOperator? {
    // Registers no backfills so it never creates an implementation.
    return null
  }

  override fun backfills(): Set<BackfillRegistration> {
    return emptySet()
  }
}
