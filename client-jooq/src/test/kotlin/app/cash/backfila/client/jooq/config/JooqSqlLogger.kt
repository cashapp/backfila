package app.cash.backfila.client.jooq.config

import misk.logging.getLogger
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.conf.Settings
import org.jooq.impl.DSL

class JooqSqlLogger : ExecuteListener {
  /**
   * Hook into the query execution lifecycle before executing queries
   */
  override fun executeStart(ctx: ExecuteContext) {
    // Create a new DSLContext for logging rendering purposes
    // This DSLContext doesn't need a connection, only the SQLDialect...
    val create = DSL.using(
      ctx.dialect(), // ... and the flag for pretty-printing
      Settings().withRenderFormatted(true),
    )

    // If we're executing a query
    if (ctx.query() != null) {
      log.info { create.renderInlined(ctx.query()) }
    } else if (ctx.routine() != null) {
      log.info { create.renderInlined(ctx.routine()) }
    }
  }

  companion object {
    val log = getLogger<JooqSqlLogger>()
  }
}
