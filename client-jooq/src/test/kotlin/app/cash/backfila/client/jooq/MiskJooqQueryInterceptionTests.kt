package app.cash.backfila.client.jooq

import app.cash.backfila.client.jooq.config.ClientJooqTestingModule
import app.cash.backfila.client.jooq.config.JooqDBIdentifier
import app.cash.backfila.client.jooq.config.JooqInterceptedMenuBackfill
import app.cash.backfila.client.jooq.config.JooqTransacter
import app.cash.backfila.client.jooq.gen.tables.references.MENU
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import jakarta.inject.Inject
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * What a [JooqQueryInterceptor] does and does not reach, run against a real database rather than asserted about in
 * isolation. The interceptor's reach is the whole point of the hook, so it is worth pinning end to end.
 */
@MiskTest(startService = true)
class MiskJooqQueryInterceptionTests {
  @MiskTestModule
  var module = ClientJooqTestingModule()

  @JooqDBIdentifier
  @Inject
  private lateinit var transacter: JooqTransacter

  @Inject
  private lateinit var backfila: Backfila

  @Test
  fun `the interceptor sees the generated queries and the backfill's own work is untouched`() {
    val expectedIds = JooqMenuBackfillDbDataSetup.createSome(transacter)

    val run = backfila.createWetRun<JooqInterceptedMenuBackfill>()
    run.execute()

    // The rewritten statements executed: the backfill found and processed its rows.
    assertThat(run.backfill.idsRanWet).containsExactlyElementsOf(expectedIds)

    val interceptedSql = run.backfill.interceptedSql
    assertThat(interceptedSql).isNotEmpty()

    // Every statement the interceptor saw is one the library generated to iterate the table.
    assertThat(interceptedSql).allSatisfy { sql ->
      assertThat(sql.trimStart()).startsWith("select")
    }

    // The backfill's own update ran on the transacter it was handed, outside the interceptor's reach.
    assertThat(interceptedSql).noneMatch { it.contains("update", ignoreCase = true) }
    assertThat(transacter.transaction("count beef") { it.fetchCount(MENU, MENU.NAME.eq("beef")) })
      .isEqualTo(expectedIds.size + 5)
  }
}
