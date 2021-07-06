package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.NoParameters
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class NoParametersTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila

  @Test
  fun `try querying no parameters config`() {
    transacter.transaction { session ->
      session.save(DbMenu("chicken"))
    }

    val run = backfila.createDryRun<RecordNoParametersConfigValuesBackfill>()
    run.execute()

    assertThat(run.backfill.configLog.single().parameters).isInstanceOf(NoParameters::class.java)
  }
}

class RecordNoParametersConfigValuesBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : HibernateBackfill<DbMenu, Id<DbMenu>, NoParameters>() {
  val configLog = mutableListOf<BackfillConfig<NoParameters>>()

  override fun backfillCriteria(config: BackfillConfig<NoParameters>): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class)
  }

  override fun runOne(pkey: Id<DbMenu>, config: BackfillConfig<NoParameters>) {
    configLog.add(config)
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
