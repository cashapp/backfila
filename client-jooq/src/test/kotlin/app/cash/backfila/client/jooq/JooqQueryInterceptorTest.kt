package app.cash.backfila.client.jooq

import app.cash.backfila.client.jooq.gen.tables.references.MENU
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.TableLike
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import org.junit.jupiter.api.Test

class JooqQueryInterceptorTest {
  @Test
  fun `transforms the SQL of queries run on the intercepted context`() {
    val executed = mutableListOf<String>()
    val dslContext = mockDslContext(executed)
    val interceptor = SqlTransformingQueryInterceptor { sql -> "$sql /* transformed */" }

    dslContext.select(MENU.ID).from(MENU).fetch()
    interceptor.intercept(dslContext).select(MENU.ID).from(MENU).fetch()

    assertThat(executed).hasSize(2)
    assertThat(executed[0]).doesNotContain("transformed")
    assertThat(executed[1]).endsWith("/* transformed */")
  }

  @Test
  fun `leaves the context it was given alone`() {
    // Deriving must not mutate the caller's context: the transacter hands the same one to the backfill's own work.
    val executed = mutableListOf<String>()
    val dslContext = mockDslContext(executed)

    SqlTransformingQueryInterceptor { sql -> "$sql /* transformed */" }.intercept(dslContext)
    dslContext.select(MENU.ID).from(MENU).fetch()

    assertThat(executed.single()).doesNotContain("transformed")
  }

  @Test
  fun `a transform that returns its input unchanged changes nothing`() {
    val executed = mutableListOf<String>()
    val dslContext = mockDslContext(executed)

    SqlTransformingQueryInterceptor { it }.intercept(dslContext).select(MENU.ID).from(MENU).fetch()

    assertThat(executed.single()).isEqualTo(dslContext.select(MENU.ID).from(MENU).sql)
  }

  @Test
  fun `inTransactionReturning hands work the intercepted context`() {
    val transacterContext = DSL.using(SQLDialect.MYSQL)
    val interceptedContext = DSL.using(SQLDialect.MYSQL)
    val backfill = FakeBackfill(transacterContext, JooqQueryInterceptor { interceptedContext })

    val received = backfill.inTransactionReturning("test", PARTITION) { it }

    assertThat(received).isSameAs(interceptedContext)
  }

  @Test
  fun `inTransactionReturning hands work the transacter's own context by default`() {
    val transacterContext = DSL.using(SQLDialect.MYSQL)
    val backfill = FakeBackfill(transacterContext)

    val received = backfill.inTransactionReturning("test", PARTITION) { it }

    assertThat(received).isSameAs(transacterContext)
    assertThat(backfill.queryInterceptor).isSameAs(JooqQueryInterceptor.NONE)
  }

  /** A [DSLContext] on a connection that records the SQL it is asked to execute and returns no rows. */
  private fun mockDslContext(executed: MutableList<String>): DSLContext {
    val provider = MockDataProvider { ctx ->
      executed += ctx.sql()
      arrayOf(MockResult(0, DSL.using(SQLDialect.MYSQL).newResult(MENU.ID)))
    }
    return DSL.using(MockConnection(provider), SQLDialect.MYSQL)
  }

  private class FakeBackfill(
    private val dslContext: DSLContext,
    override val queryInterceptor: JooqQueryInterceptor = JooqQueryInterceptor.NONE,
  ) : JooqBackfill<Long, NoParameters>() {
    private val transacter = object : BackfillJooqTransacter {
      override fun <RETURN_TYPE> transaction(
        comment: String,
        callback: (ctx: DSLContext) -> RETURN_TYPE,
      ): RETURN_TYPE = callback(dslContext)
    }

    override val shardedTransacterMap: Map<String, BackfillJooqTransacter> = mapOf(PARTITION to transacter)
    override val table: TableLike<*> = MENU
    override val compoundKeyFields: List<Field<*>> = listOf(MENU.ID)
    override val keySerializer: ByteStringSerializer<Long> = ByteStringSerializer.forLong

    override fun backfill(backfillBatch: BackfillBatch<Long, NoParameters>) = Unit
  }

  private class NoParameters

  private companion object {
    const val PARTITION = "unsharded"
  }
}
