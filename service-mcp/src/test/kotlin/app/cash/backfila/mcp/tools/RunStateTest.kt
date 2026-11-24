package app.cash.backfila.mcp.tools

import app.cash.backfila.service.persistence.BackfillState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RunStateTest {
  @Test
  fun `every backfill state has a run state`() {
    assertThat(BackfillState.entries.map { it.toRunState().name })
      .containsExactlyElementsOf(BackfillState.entries.map { it.name })
    assertThat(RunState.entries.map { it.toBackfillState().toRunState() }).containsExactlyElementsOf(RunState.entries)
  }

  @Test
  fun `percent complete rounds to one decimal and is absent until records are computed`() {
    assertThat(percentComplete(backfilled = 1, computed = 3)).isEqualTo(33.3)
    assertThat(percentComplete(backfilled = 12, computed = 10)).isEqualTo(100.0)
    assertThat(percentComplete(backfilled = 0, computed = 0)).isNull()
  }
}
