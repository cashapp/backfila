package app.cash.backfila.service.selfbackfill

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.hibernate.HibernateBackfill
import app.cash.backfila.client.misk.hibernate.PartitionProvider
import app.cash.backfila.client.misk.hibernate.UnshardedPartitionProvider
import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.DbRegisteredBackfill
import app.cash.backfila.service.persistence.DbRegisteredParameter
import app.cash.backfila.service.persistence.RegisteredBackfillQuery
import javax.inject.Inject
import misk.hibernate.Id
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.load
import wisp.logging.getLogger

internal class BackfillRegisteredParameters @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : HibernateBackfill<DbRegisteredBackfill, Id<DbRegisteredBackfill>, NoParameters>() {

  override fun runOne(pkey: Id<DbRegisteredBackfill>, config: BackfillConfig<NoParameters>) {
    transacter.transaction { session ->
      val backfill = session.load(pkey)
      if (backfill.parameters.size == backfill.parameterNames().size) {
        return@transaction
      }

      for (name in backfill.parameterNames()) {
        if (config.dryRun) {
          logger.info { "(DRY RUN) Would add parameter to backfill ${backfill.id} with name '$name'" }
        } else {
          session.save(DbRegisteredParameter(backfill, name, description = null, required = false))
        }
      }
    }
  }

  override fun partitionProvider(): PartitionProvider = UnshardedPartitionProvider(transacter)

  override fun backfillCriteria(config: BackfillConfig<NoParameters>): Query<DbRegisteredBackfill> {
    return queryFactory.newQuery(RegisteredBackfillQuery::class)
  }

  companion object {
    private val logger = getLogger<BackfillRegisteredParameters>()
  }
}
