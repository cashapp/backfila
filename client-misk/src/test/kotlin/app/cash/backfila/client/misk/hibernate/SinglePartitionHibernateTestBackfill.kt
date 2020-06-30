package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.DataClassParameterized
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.UnshardedPartitionProvider
import app.cash.backfila.protos.service.Parameter
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import okio.ByteString
import javax.inject.Inject

class SinglePartitionHibernateTestBackfill @Inject constructor(
    @ClientMiskService private val transacter: Transacter,
    private val queryFactory: Query.Factory
) : Backfill<DbMenu, Id<DbMenu>>(),
    DataClassParameterized<SinglePartitionHibernateTestBackfill.Params> {

  data class Params(
      val xyz: Int? = null,
      val shape: String = "str"
  )

  val idsRanDry = mutableListOf<Id<DbMenu>>()
  val idsRanWet = mutableListOf<Id<DbMenu>>()
  val parametersLog = mutableListOf<Map<String, ByteString>>()

  override val parameters = listOf(
      Parameter("color", "like green or blue or red"),
      Parameter("shape", "backfill shapes are square, rectangle, oval")
  )

  override fun backfillCriteria(config: BackfillConfig): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class).name("chicken")
  }

  override fun runBatch(pkeys: List<Id<DbMenu>>, config: BackfillConfig) {
    parametersLog.add(config.parameters)

    val params: Params = config.parameters()

    if (config.dryRun) {
      idsRanDry.addAll(pkeys)
    } else {
      idsRanWet.addAll(pkeys)
    }
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}