package app.cash.backfila.client.misk.setup

import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.Description
import app.cash.backfila.client.misk.jooq.BackfillBatch
import app.cash.backfila.client.misk.jooq.BackfillJooqTransacter
import app.cash.backfila.client.misk.jooq.ByteStringSerializer
import app.cash.backfila.client.misk.jooq.JooqBackfill
import app.cash.backfila.client.misk.jooq.gen.tables.references.MENU
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.TableLike
import wisp.logging.getLogger
import javax.inject.Inject

@Description("So we can backfill menus.")
class JooqMenuTestBackfill @Inject constructor(
  @JooqDBIdentifier private val jooqTransacter: JooqTransacter
) : JooqBackfill<Long, SandwichParameters>(), TestBackFill<Long, SandwichParameters> {
  override val idsRanDry = mutableListOf<Long>()
  override val idsRanWet = mutableListOf<Long>()
  override val shardedTransacterMapBackfill: Map<String, BackfillJooqTransacter>
    get() = mapOf("unsharded" to jooqTransacter)

  override val table: TableLike<*>
    get() = MENU

  override fun filterCondition(config: BackfillConfig<SandwichParameters>): Condition =
    MENU.NAME.eq(config.parameters.type)

  override val compoundKeyFields: List<Field<*>>
    get() = listOf(MENU.ID)

  override val keySerializer: ByteStringSerializer<Long>
    get() = ByteStringSerializer.forLong

  override fun backfill(backfillBatch: BackfillBatch<Long, SandwichParameters>) {
    log.info { "[keys to backfill=${backfillBatch.keys}" }

    if (backfillBatch.config.dryRun) {
      idsRanDry.addAll(backfillBatch.keys)
    } else {
      idsRanWet.addAll(backfillBatch.keys)
      jooqTransacter.transaction("") { ctx: DSLContext ->
        ctx.update(MENU)
          .set(MENU.NAME, "beef")
          .where(MENU.ID.`in`(backfillBatch.keys))
          .execute()
      }
    }
  }

  companion object {
    val log = getLogger<JooqMenuTestBackfill>()
  }
}

data class SandwichParameters(
  @Description("The type of sandwich to backfill. e.g. chicken, beef")
  val type: String = "chicken"
)
