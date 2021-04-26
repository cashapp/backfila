package app.cash.backfila.service.persistence

enum class BackfillPartitionState {
  PAUSED, // A resumable backfill partition that is not currently meant to be running
  RUNNING, // A backfill partition that is allowed to run.
  COMPLETE, // A completed partition, this is a final non-resumable state.
  STALE, // A partition that is no longer relevant, this is a final non-resumable state (possibly a split partition)
  CANCELLED; // A partition that has been manually cancelled, this is a final non-resumable state.

  companion object {
    val FINAL_STATES = setOf(STALE, CANCELLED, COMPLETE)
  }
}
