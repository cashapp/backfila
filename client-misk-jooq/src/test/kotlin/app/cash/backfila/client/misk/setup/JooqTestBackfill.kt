package app.cash.backfila.client.misk.setup

import app.cash.backfila.client.misk.jooq.BackfillBatch
import app.cash.backfila.client.misk.jooq.BackfillJooqTransacter
import app.cash.backfila.client.misk.jooq.ByteStringSerializer
import app.cash.backfila.client.misk.jooq.JooqBackfill
import app.cash.backfila.client.misk.jooq.gen.tables.references.RESTAURANTS
import org.jooq.Condition
import org.jooq.Field
import org.jooq.TableLike
import wisp.logging.getLogger
import javax.inject.Inject

data class JooqBackfillParameters(
  val param: String
)

class JooqTestBackfill @Inject constructor(
  @JooqDBIdentifier private val jooqTransacter: JooqTransacter
) : JooqBackfill<Long, JooqBackfillParameters>() {
  override val shardedTransacterMapBackfill: Map<String, BackfillJooqTransacter>
    get() = mapOf("unsharded" to jooqTransacter)

  override val table: TableLike<*>
    get() = RESTAURANTS

  override val filterCondition: Condition
    get() = RESTAURANTS.NAME.like("jooq-test-backfill-%")

  override val compoundKeyFields: List<Field<*>>
    get() = listOf(RESTAURANTS.ID)

  override val keySerializer: ByteStringSerializer<Long>
    get() = ByteStringSerializer.forLong

  override fun backfill(backfillBatch: BackfillBatch<Long, JooqBackfillParameters>) {
    log.info { "[param=${backfillBatch.config.parameters.param}]" }
  }

  companion object {
    val log = getLogger<JooqTestBackfill>()
  }
}
