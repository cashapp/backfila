package app.cash.backfila.mcp

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test

class FieldDriftTest {
  private data class Source(val name: String, val state: String, val secret: String)
  private data class Output(val backfill_name: String, val state: String)

  @Test
  fun `passes when every field is mapped, renamed, or omitted`() {
    assertThatCode {
      assertMapsEveryField(Source::class, Output::class, renamed = mapOf("name" to "backfill_name"), omitted = setOf("secret"))
    }.doesNotThrowAnyException()
  }

  @Test
  fun `fails on a dropped field`() {
    assertThatCode {
      assertMapsEveryField(Source::class, Output::class, renamed = mapOf("name" to "backfill_name"))
    }.hasMessageContaining("drops").hasMessageContaining("secret")
  }

  @Test
  fun `fails on omitted fields the source no longer has`() {
    assertThatCode {
      assertMapsEveryField(Source::class, Output::class, renamed = mapOf("name" to "backfill_name"), omitted = setOf("secret", "gone"))
    }.hasMessageContaining("gone")
  }

  @Test
  fun `fails on rename targets the output doesn't have`() {
    assertThatCode {
      assertMapsEveryField(Source::class, Output::class, renamed = mapOf("name" to "title"), omitted = setOf("secret"))
    }.hasMessageContaining("title")
  }
}
