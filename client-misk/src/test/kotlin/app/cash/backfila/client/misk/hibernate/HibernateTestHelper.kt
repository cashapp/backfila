package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.embedded.BackfillRun
import misk.hibernate.Id
import misk.hibernate.Session
import misk.hibernate.Transacter
import okhttp3.internal.toImmutableList

fun BackfillRun<*>.configureForTest() {
  this.batchSize = 10L
  this.scanSize = 100L
  this.computeCountLimit = 1L
}

fun createSome(transacter: Transacter): List<Id<DbMenu>> {
  return transacter.transaction { session: Session ->
    val expected = mutableListOf<Id<DbMenu>>()
    repeat((0..9).count()) {
      val id = session.save(DbMenu("chicken"))
      expected.add(id)
    }

    // Intersperse these to make sure we test skipping non matching records.
    repeat((0..4).count()) { session.save(DbMenu("beef")) }

    repeat((0..9).count()) {
      val id = session.save(DbMenu("chicken"))
      expected.add(id)
    }
    expected.toImmutableList()
  }
}

fun createNoMatching(transacter: Transacter) {
  transacter.transaction { session: Session ->
    repeat((0..4).count()) { session.save(DbMenu("beef")) }
  }
}
