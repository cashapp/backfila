package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class StringKeyBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject
  @ClientMiskService
  lateinit var transacter: Transacter
  @Inject
  lateinit var backfila: Backfila

  @Test
  fun `string key`() {
    transacter.transaction { session ->
      session.save(DbMenu("beef"))
      session.save(DbMenu("chicken"))
    }

    val run = backfila.createDryRun<StringKeyBackfill>()
    run.execute()

    assertThat(run.backfill.keyLog).contains("beef", "chicken")
  }

  @Test
  fun `string key with provided range`() {
    transacter.transaction { session ->
      session.save(DbMenu("beef"))
      session.save(DbMenu("chicken"))
      session.save(DbMenu("pork"))
    }

    val run = backfila.createDryRun<StringKeyBackfill>(rangeStart = "chicken", rangeEnd = "pork")
    run.execute()

    assertThat(run.backfill.keyLog).contains("chicken", "pork")
  }
}

class StringKeyBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : HibernateBackfill<DbMenu, String, NoParameters>() {
  val keyLog = mutableListOf<String>()

  override fun primaryKeyName(): String {
    return "name"
  }

  override fun primaryKeyHibernateName(): String {
    return "name"
  }

  override fun backfillCriteria(config: BackfillConfig<NoParameters>): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class)
  }

  override fun runOne(pkey: String, config: BackfillConfig<NoParameters>) {
    keyLog.add(pkey)
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
