package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.UnshardedPartitionProvider
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter

class SinglePartitionHibernateTestBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : Backfill<DbMenu, Id<DbMenu>, ShapeParameters>() {
  val idsRanDry = mutableListOf<Id<DbMenu>>()
  val idsRanWet = mutableListOf<Id<DbMenu>>()
  val parametersLog = mutableListOf<ShapeParameters>()

  override fun backfillCriteria(config: BackfillConfig<ShapeParameters>): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class).name("chicken")
  }

  override fun runBatch(pkeys: List<Id<DbMenu>>, config: BackfillConfig<ShapeParameters>) {
    parametersLog.add(config.parameters)

    if (config.dryRun) {
      idsRanDry.addAll(pkeys)
    } else {
      idsRanWet.addAll(pkeys)
    }
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
data class ShapeParameters(
  // TODO add description fields
  val color: String = "red", // "like green or blue or red"
  val shape: String = "circle" // "backfill shapes are square, rectangle, oval"
)
