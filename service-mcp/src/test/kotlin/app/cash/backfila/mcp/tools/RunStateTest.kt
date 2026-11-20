package app.cash.backfila.mcp.tools

import app.cash.backfila.service.persistence.BackfillState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RunStateTest {
  @Test
  fun `every backfill state has a run state`() {
    assertThat(BackfillState.entries.map { it.toRunState().name })
      .containsExactlyElementsOf(BackfillState.entries.map { it.name })
  }
}
