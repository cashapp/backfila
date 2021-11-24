package app.cash.backfila.client.jooq

import app.cash.backfila.client.jooq.config.JooqTransacter
import app.cash.backfila.client.jooq.gen.tables.references.MENU

object JooqMenuBackfillDbDataSetup {

  fun createNoMatching(transacter: JooqTransacter): List<Long> {
    transacter.transaction("JooqMenuBackfillDbDataSetup#createNoMatching") { session ->
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
    return transacter.transaction("JooqMenuBackfillDbDataSetup#transacter") { session ->
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
      expected
    }
  }
}
