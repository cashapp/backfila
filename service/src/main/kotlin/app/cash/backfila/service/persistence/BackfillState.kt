package app.cash.backfila.service.persistence

enum class BackfillState {
  PAUSED, // A resumable backfill that is not currently meant to be running
  RUNNING, // A backfill that is allowed to run.
  COMPLETE, // A completed backfill, this is a final non-resumable state.
  CANCELLED; // A backfill that has been manually cancelled, this is a final non-resumable state.

  companion object {
    val FINAL_STATES = setOf(CANCELLED, COMPLETE)

    /**
     * When the Backfill state changes modify the underlying partitions to these corresponding states.
     */
    fun BackfillState.getPartitionState() = when (this) {
      PAUSED -> BackfillPartitionState.PAUSED
      RUNNING -> BackfillPartitionState.RUNNING
      CANCELLED -> BackfillPartitionState.CANCELLED
      COMPLETE -> BackfillPartitionState.COMPLETE
    }
  }
}
