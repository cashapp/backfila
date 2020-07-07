package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.client.misk.UnshardedPartitionProvider
import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.createDryRun
import app.cash.backfila.client.misk.testing.assertThat
import app.cash.backfila.protos.service.Parameter
import com.google.inject.Module
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class RawParametersHibernateBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject @ClientMiskService lateinit var transacter: Transacter
  @Inject lateinit var backfila: Backfila

  @Test fun testRawParameters() {
    createSome(transacter)
    val run = backfila.createDryRun<RawParametersHibernateTestBackfill>(
        parameters = mapOf(
            "color" to "blue".encodeUtf8(),
            "shape" to "square".encodeUtf8()
        )
    )
        .apply { configureForTest() }

    run.execute()
    // Only the parameters that were actually sent are used.
    assertThat(run.backfill.parametersLog).containsExactly(
        mapOf(
            "color" to "blue".encodeUtf8(),
            "shape" to "square".encodeUtf8()
        ),
        mapOf(
            "color" to "blue".encodeUtf8(),
            "shape" to "square".encodeUtf8()
        )
    )
  }
}

class RawParametersHibernateTestBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory
) : Backfill<DbMenu, Id<DbMenu>>() {
  val idsRanDry = mutableListOf<Id<DbMenu>>()
  val idsRanWet = mutableListOf<Id<DbMenu>>()
  val parametersLog = mutableListOf<Map<String, ByteString>>()

  override val parameters = listOf(
      Parameter("color", "like green or blue or red"),
      Parameter("shape", "backfill shapes are square, rectangle, oval"),
      Parameter("vertices", "how many corners")
  )

  override fun backfillCriteria(config: BackfillConfig): Query<DbMenu> {
    return queryFactory.newQuery(MenuQuery::class).name("chicken")
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
