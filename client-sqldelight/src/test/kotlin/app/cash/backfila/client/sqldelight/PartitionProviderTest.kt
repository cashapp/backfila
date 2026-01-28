package app.cash.backfila.client.sqldelight

import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PartitionProviderTest {

  @Test
  fun `UnshardedPartitionProvider returns only partition`() {
    val provider = UnshardedPartitionProvider()
    val request = PrepareBackfillRequest.Builder().build()

    val names = provider.names(request)

    assertThat(names).containsExactly("only")
  }

  @Test
  fun `UnshardedPartitionProvider executes transaction`() {
    val provider = UnshardedPartitionProvider()
    var executed = false

    val result = provider.transaction("only") {
      executed = true
      "result"
    }

    assertThat(executed).isTrue()
    assertThat(result).isEqualTo("result")
  }

  @Test
  fun `UnshardedPartitionProvider ignores partition name in transaction`() {
    val provider = UnshardedPartitionProvider()

    // Even with a different partition name, it should still execute
    val result = provider.transaction("some-shard-name") {
      42
    }

    assertThat(result).isEqualTo(42)
  }
}
