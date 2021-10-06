package app.cash.backfila.client.jooq.config

import app.cash.backfila.client.jooq.BackfillJooqTransacter
import org.jooq.DSLContext
import org.jooq.impl.DSL
import wisp.logging.getLogger

class JooqTransacter(
  private val dslContext: DSLContext
) : BackfillJooqTransacter {

  override fun <RETURN_TYPE> transaction(
    comment: String,
    callback: (ctx: DSLContext) -> RETURN_TYPE
  ): RETURN_TYPE {
    log.info { "Executing $comment" }
    return createDSLContextAndCallback(callback)
  }

  private fun <RETURN_TYPE> createDSLContextAndCallback(
    callback: (dslContext: DSLContext) -> RETURN_TYPE
  ): RETURN_TYPE {
    return dslContext.transactionResult { configuration ->
      callback(DSL.using(configuration))
    }
  }
  companion object {
    val log = getLogger<JooqTransacter>()
  }
}
