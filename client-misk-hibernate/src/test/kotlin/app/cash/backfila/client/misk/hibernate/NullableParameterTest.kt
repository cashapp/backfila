package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
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
class NullableParameterTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject
  @ClientMiskService
  lateinit var transacter: Transacter

  @Inject
  lateinit var backfila: Backfila

  @Test
  fun `null parameter`() {
    transacter.transaction { session ->
      session.save(DbMenu("beef"))
      session.save(DbMenu("chicken"))
    }

    val run = backfila.createDryRun<NullableParameterBackfill>(
      parameters = NullableParameterBackfill.Parameters(null),
    )
    run.execute()

    assertThat(run.backfill.parameters).contains(
      NullableParameterBackfill.Parameters(null),
    )
  }

  @Test
  fun `null value to nonnullable parameter will throw`() {
    transacter.transaction { session ->
      session.save(DbMenu("beef"))
      session.save(DbMenu("chicken"))
    }

    val run = backfila.createDryRun<NullableParameterBackfill>(
      parameterData = mapOf(),
    )
    run.execute()
  }
}

class NullableParameterBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : HibernateBackfill<DbMenu, Id<DbMenu>, NullableParameterBackfill.Parameters>() {
  data class Parameters(
    val x: String?,
  )

  val parameters = mutableListOf<Parameters>()

  override fun backfillCriteria(config: BackfillConfig<Parameters>): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class)
  }

  override fun runOne(pkey: Id<DbMenu>, config: BackfillConfig<Parameters>) {
    parameters.add(config.parameters)
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
