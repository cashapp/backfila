package app.cash.backfila.client.stat

import app.cash.backfila.client.PrepareBackfillConfig

abstract class StaticDatasourceBackfill<I : Any, P : Any> : StaticDatasourceBackfillBase<I, P>() {
  /**
   * This provides the static list of items that the backfill will iterate over.
   */
  abstract val staticDatasource: List<I>

  override fun getStaticDatasource(config: PrepareBackfillConfig<P>) = staticDatasource
}
