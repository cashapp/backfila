package app.cash.backfila.client.misk.menustack

import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuItem
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createDryRun
import app.cash.backfila.embedded.createWetRun
import com.google.inject.Module
import java.util.concurrent.BlockingDeque
import javax.inject.Inject
import misk.hibernate.Query
import misk.hibernate.Session
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class MenuStackTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject @ClientMiskService
  lateinit var transacter: Transacter

  @Inject lateinit var queryFactory: Query.Factory

  @Inject lateinit var backfila: Backfila

  @MenuStack @Inject
  lateinit var menuStack: BlockingDeque<MenuItem>

  /**
   * This test helps give customers of Backfila some idea on how different backfills built on different clients
   * that perform the same underlying action can be used together.
   *
   * Gives customers an alternative to adding `in` clauses to their DB backfills that perform poorly.
   */
  @Test
  fun `shows how using a hibernate backfill and a parameter backfill a common stack of menus can be created`() {
    // Fill the database with chicken, beef and duck.
    transacter.transaction { session: Session ->
      repeat(10) { session.save(DbMenu("chicken")) }
      repeat(5) { session.save(DbMenu("beef")) }
      repeat(8) { session.save(DbMenu("duck")) }
      repeat(10) { session.save(DbMenu("chicken")) }
    }

    // Dry run all the beef.
    backfila.createDryRun<MenuStackDbBackfill>(parameters = MenuStackDbParameters(type = "beef")).execute()

    // Wet run all the chicken.
    backfila.createWetRun<MenuStackDbBackfill>(parameters = MenuStackDbParameters(type = "chicken")).execute()

    // Pick 4 duck menu ids and backfill those using a comma separated parameter.
    val pickedString = transacter.transaction { session: Session ->
      queryFactory.newQuery<MenuQuery>().name("duck").list(session).map { it.id }.take(4).joinToString(separator = ",")
    }
    backfila.createWetRun<MenuStackParametersBackfill>(parameters = MenuStackIdParameters(menuIds = pickedString)).execute()

    // Check that the singleton stack of menus is exactly 20 chicken followed by 4 duck.
    val expected = List(20) { "chicken" } + List(4) { "duck" }
    assertThat(menuStack.map { it.name }).containsExactlyElementsOf(expected)
  }
}
