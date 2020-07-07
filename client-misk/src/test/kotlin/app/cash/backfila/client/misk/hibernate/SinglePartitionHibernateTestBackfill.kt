package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.DataClassParameter
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.UnshardedPartitionProvider
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import okio.ByteString
import javax.inject.Inject

class SinglePartitionHibernateTestBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : Backfill<DbMenu, Id<DbMenu>>(),
    DataClassParameter<SandwichParams> {
  val idsRanDry = mutableListOf<Id<DbMenu>>()
  val idsRanWet = mutableListOf<Id<DbMenu>>()
  val parametersLog = mutableListOf<Map<String, ByteString>>()

  override fun backfillCriteria(config: BackfillConfig): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class).name(config.parameterData().type)
  }

  override fun runBatch(pkeys: List<Id<DbMenu>>, config: BackfillConfig) {
    parametersLog.add(config.parameters)

    if (config.dryRun) {
      idsRanDry.addAll(pkeys)
    } else {
      idsRanWet.addAll(pkeys)
    }
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}

data class SandwichParams(
  val type: String = "chicken"
)
