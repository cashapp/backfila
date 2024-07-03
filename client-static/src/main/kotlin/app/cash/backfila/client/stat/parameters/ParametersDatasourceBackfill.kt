package app.cash.backfila.client.stat.parameters

import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.stat.StaticDatasourceBackfillBase

/**
 * This backfill type is a sub variant of the [StaticDatasourceBackfillBase].
 *
 * It uses a parameter populated in the Backfila UI as the datasource for the backfill.If you have too
 * much data to fit in a parameter consider using a different client such as the S3 client.
 */
abstract class ParametersDatasourceBackfill<I : Any, P : DatasourceParameters<I>> : StaticDatasourceBackfillBase<I, P>() {
  override fun getStaticDatasource(config: PrepareBackfillConfig<P>): List<I> = config.parameters.getBackfillData()
}

interface DatasourceParameters<out I : Any> {
  /**
   * This produces the full list of data for the backfill. Make sure the element order is consistent.
   */
  fun getBackfillData(): List<I>
}

/**
 * Simple comma parameter datasource that produces a list of strings from a comma separated parameter.
 */
data class CommaParameterDatasource(
  val commaDatasource: String,
) : DatasourceParameters<String> {
  override fun getBackfillData() = commaDatasource.split(',')
}

/**
 * Simple newline parameter datasource that produces a list of strings from a newline separated parameter.
 */
data class NewlineParameterDatasource(
  val newlineDatasource: String,
) : DatasourceParameters<String> {
  override fun getBackfillData() = newlineDatasource.split('\n')
}
