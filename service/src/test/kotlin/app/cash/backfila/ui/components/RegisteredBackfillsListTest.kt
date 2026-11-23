package app.cash.backfila.ui.components

import app.cash.backfila.dashboard.RegisteredBackfill
import java.time.Instant
import misk.hotwire.buildHtml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RegisteredBackfillsListTest {
  private val createdAt = Instant.parse("2026-10-01T09:05:07Z")
  private val updatedAt = Instant.parse("2026-11-16T18:02:03Z")

  @Test
  fun `shows first and current registration timestamps with unambiguous timezone`() {
    val html = buildHtml {
      RegisteredBackfillsList(
        listOf(RegisteredBackfill("ExampleBackfill", listOf("mode"), createdAt, updatedAt)),
        "genie",
        "",
      )
    }

    assertThat(html).contains("Created at", "Updated at", "2026-10-01 09:05:07 UTC", "2026-11-16 18:02:03 UTC")
    assertThat(html).contains("datetime=\"$createdAt\"", "datetime=\"$updatedAt\"", "Custom Parameters: mode")
    assertThat(html).contains("href=\"/backfills/create/genie/ExampleBackfill/\"")
  }

  @Test
  fun `preserves variant links and escapes backfill names and parameters`() {
    val html = buildHtml {
      RegisteredBackfillsList(
        listOf(RegisteredBackfill("Example<Backfill>", listOf("mode&value"), createdAt, updatedAt)),
        "genie",
        "sandbox",
      )
    }

    assertThat(html).contains("href=\"/backfills/create/genie/sandbox/Example&lt;Backfill&gt;\"")
    assertThat(html).contains("Example&lt;Backfill&gt;", "mode&amp;value")
    assertThat(html).doesNotContain("Example<Backfill>")
  }

  @Test
  fun `shows a placeholder when the updated time is unknown`() {
    val html = buildHtml {
      RegisteredBackfillsList(
        listOf(RegisteredBackfill("ExampleBackfill", emptyList(), createdAt, null)),
        "genie",
        "",
      )
    }

    assertThat(html).contains("2026-10-01 09:05:07 UTC", "—")
    assertThat(html).doesNotContain("datetime=\"null\"")
  }
}
