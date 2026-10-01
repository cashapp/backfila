package app.cash.backfila.client.jooq.config

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.jooq.BackfillBatch
import app.cash.backfila.client.jooq.BackfillJooqTransacter
import app.cash.backfila.client.jooq.ByteStringSerializer
import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.client.jooq.JooqQueryInterceptor
import app.cash.backfila.client.jooq.SqlTransformingQueryInterceptor
import app.cash.backfila.client.jooq.gen.tables.references.MENU
import jakarta.inject.Inject
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.TableLike

/**
 * Records the SQL its [queryInterceptor] is handed, so a test can see which statements the interceptor reaches. Writes a
 * row of its own in [backfill] on the transacter it is given, which the interceptor should *not* reach.
 */
@Description("So we can see which queries an interceptor is applied to.")
class JooqInterceptedMenuBackfill @Inject constructor(
  @JooqDBIdentifier private val jooqTransacter: JooqTransacter,
) : JooqBackfill<Long, NoParameters>(), IdRecorder<Long, NoParameters> {
  override val idsRanDry = mutableListOf<Long>()
  override val idsRanWet = mutableListOf<Long>()

  /** Every statement the interceptor saw, in order. */
  val interceptedSql = mutableListOf<String>()

  override val queryInterceptor: JooqQueryInterceptor =
    SqlTransformingQueryInterceptor { sql ->
      interceptedSql += sql
      // A trailing comment is valid MySQL, so the rewritten statement still has to execute for these tests to pass.
      "$sql /* intercepted */"
    }

  override val shardedTransacterMap: Map<String, BackfillJooqTransacter>
    get() = mapOf("unsharded" to jooqTransacter)

  override val table: TableLike<*>
    get() = MENU

  override fun filterCondition(config: BackfillConfig<NoParameters>): Condition = MENU.NAME.eq("chicken")

  override val compoundKeyFields: List<Field<*>>
    get() = listOf(MENU.ID)

  override val keySerializer: ByteStringSerializer<Long>
    get() = ByteStringSerializer.forLong

  override fun backfill(backfillBatch: BackfillBatch<Long, NoParameters>) {
    if (backfillBatch.config.dryRun) {
      idsRanDry.addAll(backfillBatch.keys)
      return
    }
    idsRanWet.addAll(backfillBatch.keys)
    jooqTransacter.transaction("JooqInterceptedMenuBackfill#backfill") { ctx: DSLContext ->
      ctx.update(MENU)
        .set(MENU.NAME, "beef")
        .where(MENU.ID.`in`(backfillBatch.keys))
        .execute()
    }
  }
}
