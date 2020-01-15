package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbOrder
import app.cash.backfila.client.misk.OrderQuery
import app.cash.backfila.client.misk.VitessShardedInstanceProvider
import app.cash.backfila.client.misk.embedded.Backfila
import com.google.inject.Module
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject

@MiskTest(startService = true)
class VitessSingleCursorBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(true, listOf(TestBackfill::class))

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila

  private class TestBackfill @Inject constructor(
    @ClientMiskService private val transacter: Transacter,
    private val queryFactory: Query.Factory) : Backfill<DbOrder, Id<DbOrder>>() {
    val idsRanDry = mutableListOf<Id<DbOrder>>()
    val idsRanWet = mutableListOf<Id<DbOrder>>()

    override fun backfillCriteria(config: BackfillConfig): Query<DbOrder> {
      val name = (config.parameters["name"] ?: "chickfila".encodeUtf8()).utf8()
      if (name.isEmpty()) {
        return queryFactory.newQuery(OrderQuery::class)
      }
      return queryFactory.newQuery(OrderQuery::class).restaurantName(name)
    }

    override fun runBatch(pkeys: List<Id<DbOrder>>, config: BackfillConfig) {
      if (config.dryRun) {
        idsRanDry.addAll(pkeys)
      } else {
        idsRanWet.addAll(pkeys)
      }
    }

    override fun instanceProvider() = VitessShardedInstanceProvider(transacter, this)
  }
}
