package app.cash.backfila.service.scheduler

import app.cash.backfila.service.persistence.BackfilaDb
import app.cash.backfila.service.persistence.BackfillPartitionState
import app.cash.backfila.service.persistence.RunPartitionQuery
import app.cash.backfila.service.runner.BackfillRunner
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.tokens.TokenGenerator

@Singleton
class LeaseHunter @Inject constructor(
  @BackfilaDb private val transacter: Transacter,
  private val queryFactory: Query.Factory,
  private val clock: Clock,
  private val tokenGenerator: TokenGenerator,
  private val backfillRunnerFactory: BackfillRunner.Factory
) {
  fun hunt(): Set<BackfillRunner> {
    // Hibernate prevents write races using the version column, ensuring only one transaction
    // will win the lease.
    return transacter.transaction { session ->
      val unleasedPartitions = queryFactory.newQuery<RunPartitionQuery>()
        .runState(BackfillPartitionState.RUNNING)
        .leaseExpiresAtBefore(clock.instant())
        .list(session)

      if (unleasedPartitions.isEmpty()) {
        return@transaction setOf()
      }

      // Pick one randomly to run, another host will be less likely to pick the same one.
      val partition = unleasedPartitions.random()
      val leaseToken = tokenGenerator.generate()
      partition.lease_token = leaseToken
      partition.lease_expires_at = clock.instant() + LEASE_DURATION

      // Only get one lease at a time to promote distribution of work and to ramp up
      // the backfill slowly.
      setOf(
        backfillRunnerFactory.create(
          session,
          partition,
          leaseToken
        )
      )
    }
  }

  companion object {
    val LEASE_DURATION: Duration = Duration.ofMinutes(5)
  }
}
