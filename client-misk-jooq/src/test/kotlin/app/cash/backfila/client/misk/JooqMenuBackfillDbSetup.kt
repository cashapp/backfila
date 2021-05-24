package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.jooq.gen.tables.references.MENU
import app.cash.backfila.client.misk.setup.JooqTransacter
import okhttp3.internal.toImmutableList

object JooqMenuBackfillDbSetup {

  fun createNoMatching(transacter: JooqTransacter): List<Long> {
    transacter.transaction("") { session ->
      repeat((0..4).count()) {
        session.newRecord(MENU)
          .apply {
            this.name = "beef"
          }.let {
            it.store()
            it.id!!
          }
      }
    }
    return emptyList()
  }

  fun createSome(transacter: JooqTransacter): List<Long> {
    return transacter.transaction("") { session ->
      val expected = mutableListOf<Long>()
      repeat((0..9).count()) {
        session.newRecord(MENU)
          .apply {
            this.name = "chicken"
          }.let {
            it.store()
            it.id!!
          }.apply {
            expected.add(this)
          }
      }

      // Intersperse these to make sure we test skipping non matching records.
      repeat((0..4).count()) {
        session.newRecord(MENU)
          .apply {
            this.name = "beef"
          }.let {
            it.store()
            it.id!!
          }
      }

      repeat((0..9).count()) {
        session.newRecord(MENU)
          .apply {
            this.name = "chicken"
          }.let {
            it.store()
            it.id!!
          }.apply {
            expected.add(this)
          }
      }
      expected.toImmutableList()
    }
  }
}
