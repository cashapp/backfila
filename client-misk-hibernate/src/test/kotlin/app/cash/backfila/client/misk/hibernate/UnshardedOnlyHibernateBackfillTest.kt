package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbActiveCoupon
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.BackfillRun
import app.cash.backfila.embedded.createWetRun
import com.google.inject.Module
import java.time.Instant
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * This holds edge case hibernate tests.
 */
@MiskTest(startService = true)
class UnshardedOnlyHibernateBackfillTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject @ClientMiskService
  lateinit var transacter: Transacter

  @Inject lateinit var backfila: Backfila

  @Test fun `where annotation hibernate class counts scanned ids correctly`() {
    val couponIds = createCoupons(100)
    couponIds.filterIndexed { index, _ -> index % 2 == 1 }.forEach { expireCoupon(it) }
    val run = backfila.createWetRun<ActiveCouponBackfill>()
      .apply { configureForTest() }
    run.execute()
    assertThat(run.backfill.couponsToProcess).size().isEqualTo(50)
    assertThat(run.precomputeMatchingCount).isEqualTo(50L)
    assertThat(run.precomputeScannedCount).isEqualTo(100)
  }

  @Test fun `where annotation hibernate class works with projection gaps`() {
    // We had a problem with a @Where annotation was limiting the view on all but one query that
    // needs to be manually written and caused a null exception in a later query. We fixed this by
    // manually writing both queries that require a complete view of the table. This test checks
    // this case going forward.
    createCoupons(20)
    val couponIdsToExpire = createCoupons(50)
    createCoupons(20)
    couponIdsToExpire.forEach { expireCoupon(it) }
    val run = backfila.createWetRun<ActiveCouponBackfill>()
      .apply { configureForTest() }
    run.execute()
    assertThat(run.backfill.couponsToProcess).size().isEqualTo(40L)
    assertThat(run.precomputeMatchingCount).isEqualTo(40L)
    assertThat(run.precomputeScannedCount).isEqualTo(90L)
  }

  private fun BackfillRun<*>.configureForTest() {
    this.batchSize = 5L
    this.scanSize = 10L
    this.computeCountLimit = 1L
  }

  private fun createCoupons(times: Int): List<Id<DbActiveCoupon>> {
    val expected = mutableListOf<Id<DbActiveCoupon>>()
    transacter.transaction { session ->
      repeat(times) {
        expected.add(session.save(DbActiveCoupon()))
      }
    }
    return expected
  }

  private fun expireCoupon(couponId: Id<DbActiveCoupon>) {
    transacter.transaction { session ->
      session.load(couponId).apply {
        this.expired_at = Instant.now()
        session.save(this)
      }
    }
  }
}

@Description("To process active coupons.")
class ActiveCouponBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : HibernateBackfill<DbActiveCoupon, Id<DbActiveCoupon>, NoParameters>() {
  val couponsToProcess = mutableSetOf<Id<DbActiveCoupon>>()

  override fun backfillCriteria(config: BackfillConfig<NoParameters>): Query<DbActiveCoupon> {
    return queryFactory.dynamicQuery(DbActiveCoupon::class)
  }

  override fun runBatch(pkeys: List<Id<DbActiveCoupon>>, config: BackfillConfig<NoParameters>) {
    if (!config.dryRun) {
      couponsToProcess.addAll(pkeys)
    }
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
