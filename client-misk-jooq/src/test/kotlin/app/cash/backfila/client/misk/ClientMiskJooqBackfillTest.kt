package app.cash.backfila.client.misk

import app.cash.backfila.client.misk.embedded.Backfila
import app.cash.backfila.client.misk.embedded.createWetRun
import app.cash.backfila.client.misk.jooq.gen.tables.references.RESTAURANTS
import app.cash.backfila.client.misk.setup.ClientJooqTestingModule
import app.cash.backfila.client.misk.setup.JooqBackfillParameters
import app.cash.backfila.client.misk.setup.JooqDBIdentifier
import app.cash.backfila.client.misk.setup.JooqTestBackfill
import app.cash.backfila.client.misk.setup.JooqTransacter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import wisp.logging.LogCollector
import javax.inject.Inject

@MiskTest(startService = true)
class ClientMiskJooqBackfillTest {
  @MiskTestModule var module = ClientJooqTestingModule()
  @JooqDBIdentifier
  @Inject private lateinit var transacter: JooqTransacter
  @Inject private lateinit var logCollector: LogCollector
  @Inject private lateinit var backfila: Backfila

  @Test fun `params are passed down to the backfill`() {
    backfila.createWetRun<JooqTestBackfill>(
      parameters = JooqBackfillParameters(param = "this is a param")
    ).execute()

    assertThat(logCollector.takeMessages(JooqTestBackfill::class)).satisfies { messages ->
      assertThat(messages.any { it.contains("[param=this is a param") }).isTrue()
    }
  }

  @BeforeEach fun seedData() {
    transacter.transaction("seeding") { dslContext ->
      (1..10).forEach { index ->
        dslContext.newRecord(RESTAURANTS).apply {
          this.name = if (index % 2 == 0) "jooq-test-backfill-$index"
          else "wont be touched by backfill-$index"
        }.also { it.insert() }
      }
    }
  }
}
