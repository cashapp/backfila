package app.cash.backfila.client.jooq

import app.cash.backfila.client.Backfill
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.BackfillRun
import app.cash.backfila.client.jooq.config.JooqMenuTestBackfill
import app.cash.backfila.client.jooq.config.JooqTransacter
import app.cash.backfila.client.jooq.config.JooqWidgetCompoundKeyBackfill
import misk.time.FakeClock
import misk.tokens.TokenGenerator
import okio.ByteString
import java.util.stream.Stream
import kotlin.reflect.KClass

object MiskJooqBackfillTestProviders {

  @JvmStatic
  fun emptyTable(): Stream<JooqBackfillTestOptions<*, *>> {
    return Stream.of(
      JooqBackfillTestOptions(
        backfillClass = JooqMenuTestBackfill::class,
        backfillRowKeys = { _, _, _ ->
          emptyList()
        }
      ),
      JooqBackfillTestOptions(
        backfillClass = JooqWidgetCompoundKeyBackfill::class,
        backfillRowKeys = { _, _, _ ->
          emptyList()
        }
      )
    )
  }

  @JvmStatic
  fun noMatching(): Stream<JooqBackfillTestOptions<*, *>> {
    return Stream.of(
      JooqBackfillTestOptions(
        backfillClass = JooqMenuTestBackfill::class,
        backfillRowKeys = { transacter, _, _ ->
          JooqMenuBackfillDbDataSetup.createNoMatching(transacter)
        }
      ),
      JooqBackfillTestOptions(
        backfillClass = JooqWidgetCompoundKeyBackfill::class,
        backfillRowKeys = { transacter, clock, tokenGenerator ->
          WidgetCompoundKeyBackfillDbDataSetup.createNoMatching(
            transacter,
            clock,
            tokenGenerator
          )
        }
      )
    )
  }

  @JvmStatic
  fun createSome(): Stream<JooqBackfillTestOptions<*, *>> {
    return Stream.of(
      JooqBackfillTestOptions(
        backfillClass = JooqMenuTestBackfill::class,
        backfillRowKeys = { transacter, _, _ ->
          JooqMenuBackfillDbDataSetup.createSome(transacter)
        }
      ),
      JooqBackfillTestOptions(
        backfillClass = JooqWidgetCompoundKeyBackfill::class,
        backfillRowKeys = { transacter, clock, tokenGenerator ->
          WidgetCompoundKeyBackfillDbDataSetup.createSome(
            transacter,
            clock,
            tokenGenerator
          )
        }
      )
    )
  }
}

data class JooqBackfillTestOptions<K : Any, BackfillType : Backfill>(
  val backfillClass: KClass<BackfillType>,
  val backfillRowKeys: (
    transacter: JooqTransacter,
    clock: FakeClock,
    tokenGenerator: TokenGenerator
  ) -> List<K>,
  val description: String = "",
  val parameterData: Map<String, ByteString> = mapOf()
) {
  fun createDryRun(
    backfila: Backfila,
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<BackfillType> {
    return backfila.createDryRun(backfillClass, parameterData, rangeStart, rangeEnd)
  }

  fun createWetRun(
    backfila: Backfila,
    rangeStart: String? = null,
    rangeEnd: String? = null
  ): BackfillRun<BackfillType> {
    return backfila.createWetRun(backfillClass, parameterData, rangeStart, rangeEnd)
  }

  override fun toString(): String {
    return this.backfillClass.simpleName!!
  }
}
