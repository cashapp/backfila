package app.cash.backfila.client.jooq

import app.cash.backfila.client.jooq.gen.tables.references.WIDGETS
import app.cash.backfila.client.jooq.config.CompoundKey
import app.cash.backfila.client.jooq.config.JooqTransacter
import misk.time.FakeClock
import misk.tokens.TokenGenerator

object WidgetCompoundKeyBackfillDbDataSetup {

  fun createNoMatching(
    transacter: JooqTransacter,
    clock: FakeClock,
    tokenGenerator: TokenGenerator
  ): List<CompoundKey> {
    transacter.transaction("WidgetCompoundKeyBackfillDbDataSetup#createNoMatching") { session ->
      (0..4).mapIndexed { index, _ ->
        session.newRecord(WIDGETS)
          .apply {
            this.name = "beef"
            this.manufacturerToken = "not selected for backfill"
            this.createdAtMs = clock.instant()
              .plusSeconds(index.toLong()).toEpochMilli()
            this.widgetToken = tokenGenerator.generate().encodeToByteArray()
            store()
          }.map { CompoundKey.recordToKey(it) }
      }
    }
    return emptyList()
  }

  fun createSome(
    transacter: JooqTransacter,
    clock: FakeClock,
    tokenGenerator: TokenGenerator
  ): List<CompoundKey> {
    return transacter.transaction("WidgetCompoundKeyBackfillDbDataSetup#createSome") { session ->
      val expected = mutableListOf<CompoundKey>()
      (0..9).mapIndexed { index, _ ->
        session.newRecord(WIDGETS)
          .apply {
            this.name = "chicken"
            this.manufacturerToken = "token1 - select for backfill"
            this.createdAtMs = clock.instant().plusSeconds(index.toLong()).toEpochMilli()
            this.widgetToken = tokenGenerator.generate().encodeToByteArray()
            store()
          }.map { CompoundKey.recordToKey(it) }
      }.also {
        expected.addAll(it)
      }

      var lastIndex = 10
      // Intersperse these to make sure we test skipping non matching records.
      (0..4).mapIndexed { index, _ ->
        session.newRecord(WIDGETS)
          .apply {
            this.name = "beef"
            this.manufacturerToken = "token2 - not select for backfill"
            this.createdAtMs = clock.instant()
              .plusSeconds(lastIndex + index.toLong()).toEpochMilli()
            this.widgetToken = tokenGenerator.generate().encodeToByteArray()
            store()
          }.map { CompoundKey.recordToKey(it) }
      }

      lastIndex = 15
      (0..9).mapIndexed { index, _ ->
        session.newRecord(WIDGETS)
          .apply {
            this.name = "chicken"
            this.manufacturerToken = "token3 - select for backfill"
            this.createdAtMs = clock.instant()
              .plusSeconds(lastIndex + index.toLong()).toEpochMilli()
            this.widgetToken = tokenGenerator.generate().encodeToByteArray()
            store()
          }.map { CompoundKey.recordToKey(it) }
      }.also {
        expected.addAll(it)
      }
      expected
    }
  }
}
