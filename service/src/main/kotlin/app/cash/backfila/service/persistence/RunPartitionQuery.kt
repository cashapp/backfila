package app.cash.backfila.service.persistence

import java.time.Instant
import misk.hibernate.Constraint
import misk.hibernate.Id
import misk.hibernate.Operator
import misk.hibernate.Order
import misk.hibernate.Query

interface RunPartitionQuery : Query<DbRunPartition> {
  @Constraint("backfill_run_id")
  fun backfillRunId(backfillRunId: Id<DbBackfillRun>): RunPartitionQuery

  @Constraint("backfill_run_id", Operator.IN)
  fun backfillRunIdIn(backfillRunIds: Collection<Id<DbBackfillRun>>): RunPartitionQuery

  @Constraint("run_state")
  fun runState(runState: BackfillPartitionState): RunPartitionQuery

  @Constraint("lease_expires_at", Operator.LT)
  fun leaseExpiresAtBefore(time: Instant): RunPartitionQuery

  @Order("partition_name")
  fun orderByName(): RunPartitionQuery
}
