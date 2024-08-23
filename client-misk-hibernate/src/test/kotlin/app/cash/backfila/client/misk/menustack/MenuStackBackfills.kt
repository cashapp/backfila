package app.cash.backfila.client.misk.menustack

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuItem
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.hibernate.HibernateBackfill
import app.cash.backfila.client.misk.hibernate.UnshardedPartitionProvider
import app.cash.backfila.client.stat.parameters.DatasourceParameters
import app.cash.backfila.client.stat.parameters.ParametersDatasourceBackfill
import java.util.concurrent.BlockingDeque
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery

/**
 * Backfills all menus of a specific type to the singleton menuStack.
 */
class MenuStackDbBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val menuProcessor: MenuProcessor,
) : HibernateBackfill<DbMenu, Id<DbMenu>, MenuStackDbParameters>() {
  override fun backfillCriteria(config: BackfillConfig<MenuStackDbParameters>): Query<DbMenu> {
    return queryFactory.newQuery<MenuQuery>().name(config.parameters.type)
  }

  override fun runOne(pkey: Id<DbMenu>, config: BackfillConfig<MenuStackDbParameters>) {
    menuProcessor.addToStack(pkey, config)
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}

/**
 * Backfills all menus specified by ID in the backfill parameters to the singleton menuStack.
 */
class MenuStackParametersBackfill @Inject constructor(
  private val menuProcessor: MenuProcessor,
) : ParametersDatasourceBackfill<Id<DbMenu>, MenuStackIdParameters>() {
  override fun runOne(item: Id<DbMenu>, config: BackfillConfig<MenuStackIdParameters>) {
    menuProcessor.addToStack(item, config)
  }
}

/**
 * Processes a menu and places it onto the menuStack.
 */
class MenuProcessor @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  @MenuStack private val menuStack: BlockingDeque<MenuItem>,
) {
  fun addToStack(item: Id<DbMenu>, config: BackfillConfig<*>) {
    val menuItem = transacter.transaction { session ->
      queryFactory.newQuery<MenuQuery>().id(item).list(session).single().menuItem()
    }
    if (!config.dryRun) {
      menuStack.add(menuItem)
    }
  }
}

data class MenuStackDbParameters(
  /**
   * beef, chicken, duck ....
   */
  val type: String,
)

data class MenuStackIdParameters(
  val menuIds: String,
) : DatasourceParameters<Id<DbMenu>> {
  override fun getBackfillData(): List<Id<DbMenu>> {
    return menuIds.split(',').map { Id(it.toLong()) }
  }
}
