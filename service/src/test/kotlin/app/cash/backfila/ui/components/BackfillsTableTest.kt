package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.UiBackfillRun
import app.cash.backfila.service.persistence.BackfillState
import java.time.Instant
import misk.hotwire.buildHtml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BackfillsTableTest {
  @Test
  fun `recent table shows run details without paused label or delete toggle`() {
    val createdAt = Instant.parse("2026-11-16T12:00:00Z")
    val html = buildHtml {
      BackfillsTable(
        backfills = listOf(
          UiBackfillRun("42", "ExampleBackfill", BackfillState.RUNNING, false, createdAt, "molly", createdAt, true, 100, 25),
        ),
        title = "Your recent backfills",
      )
    }

    assertThat(html).contains("Your recent backfills", "ExampleBackfill", "RUNNING", "25%", createdAt.toString(), "Created at")
    assertThat(html).contains("href=\"/backfills/42\"")
    assertThat(html).doesNotContain("Backfills (Paused)", "Show deleted", "<form")
  }

  @Test
  fun `existing paused table keeps its title and delete toggle`() {
    val html = buildHtml { BackfillsTable(running = false, backfills = emptyList()) }
    assertThat(html).contains("Backfills (Paused)", "Show deleted")
  }
}
