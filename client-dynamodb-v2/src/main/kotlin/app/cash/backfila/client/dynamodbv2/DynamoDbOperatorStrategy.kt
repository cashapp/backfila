package app.cash.backfila.client.dynamodbv2

import java.time.Duration

/**
 * Describes how to run the backfill - operational configuration for the backfill execution.
 * This includes performance settings and operational parameters.
 */
open class DynamoDbOperatorStrategy(
  /**
   * The number of independent workers to perform the backfill. When the Backfill is executing, each
   * worker runs 1 or more batches concurrently. Set a low number here to reduce the total tracking
   * overhead in Backfila; set a higher number for more concurrency. The default of 8 means that
   * the Backfill will run at least 8 batches concurrently.
   */
  val partitionCount: Int = 8,

  /**
   * Override this to force Backfila to run this number of batches in total, divided among the
   * partitions.
   *
   * If null, Backfila will use a dynamic segment count. This automatically guesses the segment
   * count to fit the requested batch size. Override this if the guess is bad, such as when your
   * data is not uniformly distributed.
   */
  val fixedSegmentCount: Int? = null,

  /**
   * Configures how long a `runBatch` call is allowed to paginate through a scan segment before we
   * pause the pagination and respond to the Backfila server. The response creates a checkpoint; a
   * future `runBatch` call will resume from that point in the segment. Defaults to 2 seconds.
   */
  val checkpointSegmentProgressAfter: Duration = Duration.ofSeconds(2),

  /**
   * It is rather easy to run a backfill against a dynamo instance that is configured expensively.
   * Update dynamo so the billing mode is PROVISIONED rather than PAY_PER_REQUEST as the latter can
   * be very expensive.
   */
  val mustHaveProvisionedBillingMode: Boolean = true,
) {
  init {
    require(partitionCount > 0) { "Partition count must be positive" }
  }

  companion object {
    @JvmStatic
    fun builder(): Builder {
      return Builder()
    }
  }

  class Builder {
    private var partitionCount: Int = 8
    private var fixedSegmentCount: Int? = null
    private var checkpointSegmentProgressAfter: Duration = Duration.ofSeconds(2)
    private var mustHaveProvisionedBillingMode: Boolean = true

    /** See [DynamoDbOperatorStrategy.partitionCount]. */
    fun partitionCount(partitionCount: Int): Builder {
      this.partitionCount = partitionCount
      return this
    }

    /** See [DynamoDbOperatorStrategy.fixedSegmentCount]. */
    fun fixedSegmentCount(segmentCount: Int?): Builder {
      this.fixedSegmentCount = segmentCount
      return this
    }

    /** See [DynamoDbOperatorStrategy.checkpointSegmentProgressAfter]. */
    fun checkpointSegmentProgressAfter(duration: Duration): Builder {
      this.checkpointSegmentProgressAfter = duration
      return this
    }

    /** See [DynamoDbOperatorStrategy.mustHaveProvisionedBillingMode]. */
    fun mustHaveProvisionedBillingMode(mustHaveProvisionedBillingMode: Boolean): Builder {
      this.mustHaveProvisionedBillingMode = mustHaveProvisionedBillingMode
      return this
    }

    fun build(): DynamoDbOperatorStrategy {
      return DynamoDbOperatorStrategy(
        partitionCount = partitionCount,
        fixedSegmentCount = fixedSegmentCount,
        checkpointSegmentProgressAfter = checkpointSegmentProgressAfter,
        mustHaveProvisionedBillingMode = mustHaveProvisionedBillingMode,
      )
    }
  }
}
