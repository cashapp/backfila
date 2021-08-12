package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.BackfilaManagementClient
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.NoParameters
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class BackfilaManagementClientTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject internal lateinit var managementClient: BackfilaManagementClient

  @Test
  fun `start and create`() {
    val id = transacter.transaction { session ->
      session.save(DbMenu("chicken"))
    }

    managementClient.createAndStart(ChickenToBeefBackfill::class.java, dry_run = false)

    val name = transacter.transaction { session ->
      session.load(id).name
    }
    assertThat(name).isEqualTo("beef")
  }
}

class ChickenToBeefBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : HibernateBackfill<DbMenu, Id<DbMenu>, NoParameters>() {

  override fun backfillCriteria(config: BackfillConfig<NoParameters>): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class)
      .name("chicken")
  }

  override fun runOne(pkey: Id<DbMenu>, config: BackfillConfig<NoParameters>) {
    transacter.transaction { session ->
      session.load(pkey).name = "beef"
    }
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
