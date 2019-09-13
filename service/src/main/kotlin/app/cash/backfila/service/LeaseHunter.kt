package app.cash.backfila.service

import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.tokens.TokenGenerator
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

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
      val unleasedInstances = queryFactory.newQuery<RunInstanceQuery>()
          .runState(BackfillState.RUNNING)
          .leaseExpiresAtBefore(clock.instant())
          .list(session)

      if (unleasedInstances.isEmpty()) {
        return@transaction setOf()
      }

      // Pick one randomly to run, another host will be less likely to pick the same one.
      val instance = unleasedInstances.random()
      val leaseToken = tokenGenerator.generate()
      instance.lease_token = leaseToken
      instance.lease_expires_at = clock.instant() + LEASE_DURATION

      // Only get one lease at a time to promote distribution of work and to ramp up
      // the backfill slowly.
      setOf(backfillRunnerFactory.create(
          session,
          instance,
          leaseToken
      ))
    }
  }

  companion object {
    val LEASE_DURATION: Duration = Duration.ofMinutes(5)
  }
}