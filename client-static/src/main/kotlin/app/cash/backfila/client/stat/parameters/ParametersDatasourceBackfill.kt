package app.cash.backfila.client.stat.parameters

import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.client.stat.StaticDatasourceBackfillBase

/**
 * This backfill type is a sub variant of the [StaticDatasourceBackfillBase]. If you have too
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
 * Simple CSV datasource that produces a list of strings from a CSV parameter.
 */
data class CsvDatasourceParameters(
  val csvData: String,
) : DatasourceParameters<String> {
  override fun getBackfillData() = csvData.split(',')
}
