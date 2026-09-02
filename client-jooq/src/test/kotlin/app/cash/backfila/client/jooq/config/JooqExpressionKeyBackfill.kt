package app.cash.backfila.client.jooq.config

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.jooq.BackfillBatch
import app.cash.backfila.client.jooq.BackfillJooqTransacter
import app.cash.backfila.client.jooq.ByteStringSerializer
import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.client.jooq.gen.tables.references.MENU
import jakarta.inject.Inject
import org.jooq.Condition
import org.jooq.Field
import org.jooq.TableLike

/**
 * [compoundKeyFields] is a derived expression rather than a plain column, so it has no usable
 * column name. Batching must not depend on one.
 */
@Description("So we can backfill menus keyed by an expression.")
class JooqExpressionKeyBackfill @Inject constructor(
  @JooqDBIdentifier private val jooqTransacter: JooqTransacter,
) : JooqBackfill<Long, SandwichParameters>(), IdRecorder<Long, SandwichParameters> {
  override val idsRanDry = mutableListOf<Long>()
  override val idsRanWet = mutableListOf<Long>()
  override val shardedTransacterMap: Map<String, BackfillJooqTransacter>
    get() = mapOf("unsharded" to jooqTransacter)

  override val table: TableLike<*>
    get() = MENU

  override fun filterCondition(config: BackfillConfig<SandwichParameters>): Condition =
    MENU.NAME.eq(config.parameters.type)

  override val compoundKeyFields: List<Field<*>>
    get() = listOf(MENU.ID.cast(Long::class.java))

  override val keySerializer: ByteStringSerializer<Long>
    get() = ByteStringSerializer.forLong

  override fun backfill(backfillBatch: BackfillBatch<Long, SandwichParameters>) {
    if (backfillBatch.config.dryRun) {
      idsRanDry.addAll(backfillBatch.keys)
    } else {
      idsRanWet.addAll(backfillBatch.keys)
    }
  }
}
