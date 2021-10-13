package app.cash.backfila.client.jooq

import org.jooq.DSLContext

interface BackfillJooqTransacter {
  fun <RETURN_TYPE> transaction(
    comment: String,
    callback: (ctx: DSLContext) -> RETURN_TYPE
  ): RETURN_TYPE
}
