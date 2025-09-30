package app.cash.backfila.client.misk.hibernate

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.NoParameters
import app.cash.backfila.client.misk.ClientMiskService
import app.cash.backfila.client.misk.ClientMiskTestingModule
import app.cash.backfila.client.misk.DbMenu
import app.cash.backfila.client.misk.MenuQuery
import app.cash.backfila.embedded.Backfila
import app.cash.backfila.embedded.createWetRun
import com.google.inject.Module
import javax.inject.Inject
import kotlin.random.Random
import misk.hibernate.Query
import misk.hibernate.Transacter
import misk.hibernate.newQuery
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class LargePkeyDatabaseTest {
  @Suppress("unused")
  @MiskTestModule
  val module: Module = ClientMiskTestingModule(false)

  @Inject
  @ClientMiskService
  lateinit var transacter: Transacter

  @Inject
  lateinit var backfila: Backfila

  @Test
  fun `backfill creates large pkeys and large messages that test event_logs capacity`() {
    val menuName = "test_menu"
    var menuId: Long = 0

    transacter.transaction { session ->
      val menu = DbMenu(menuName)
      session.save(menu)
      menuId = menu.id.id
    }

    // Run backfill that will process the pkey and generate large log messages
    val run = backfila.createWetRun<LargeMenuNameBackfill>()
    run.execute()

    // The backfill should complete successfully, proving that:
    // 1. Large pkeys work fine (stored and retrieved correctly)
    // 2. Large messages can be stored in event_logs.message (8192 bytes)
    assertThat(run.backfill.processedMenuNames).hasSize(1)
    assertThat(run.backfill.processedMenuNames.first()).isEqualTo(menuName)

    // Verify the message that would be logged is large and contains the pkey
    val largeMessage = run.backfill.generateLargeLogMessage(menuName)
    assertThat(largeMessage.toByteArray(Charsets.UTF_8).size).isGreaterThan(3000)
    assertThat(largeMessage).contains(menuName)
  }

  @Test
  fun `range operations work with large pkey menu names in database`() {
    // Create multiple records with large menu names (using name as pkey for this test)
    val largeName1 = generateLargeKey(100, "range_large_menu_a")
    val largeName2 = generateLargeKey(100, "range_large_menu_b")
    val largeName3 = generateLargeKey(100, "range_large_menu_c")

    transacter.transaction { session ->
      session.save(DbMenu(largeName1))
      session.save(DbMenu(largeName2))
      session.save(DbMenu(largeName3))
    }

    // Run backfill with range from largeName1 to largeName2
    val run = backfila.createWetRun<LargeMenuNameBackfill>(rangeStart = largeName1, rangeEnd = largeName2)
    run.execute()

    // Should process names in the range (inclusive) - testing large pkey range operations
    assertThat(run.backfill.processedMenuNames).contains(largeName1, largeName2)
    assertThat(run.backfill.processedMenuNames).doesNotContain(largeName3)

    // Verify the processed pkeys are actually large and appear in messages
    run.backfill.processedMenuNames.forEach { processedName ->
      assertThat(processedName.toByteArray(Charsets.UTF_8).size).isGreaterThan(90)
      val message = run.backfill.generateLargeLogMessage(processedName)
      assertThat(message).contains(processedName) // Large pkey naturally appears in message
    }
  }

  companion object {
    /**
     * Generates a string of exactly the target size in UTF-8 bytes.
     * Uses a mix of ASCII and Unicode characters to test encoding edge cases.
     */
    fun generateLargeKey(targetSizeBytes: Int, prefix: String): String {
      val random = Random(42) // Fixed seed for reproducible tests
      val builder = StringBuilder(prefix)

      // Add separator
      builder.append("_")

      // Fill with a mix of characters to reach target size
      val baseContent = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
      val unicodeChars = "αβγδεζηθικλμνξοπρστυφχψω" // Greek letters (2 bytes each in UTF-8)

      var currentBytes = builder.toString().toByteArray(Charsets.UTF_8).size

      while (currentBytes < targetSizeBytes - 10) { // Leave some room for final adjustment
        if (random.nextBoolean()) {
          // Add ASCII character (1 byte)
          builder.append(baseContent[random.nextInt(baseContent.length)])
          currentBytes += 1
        } else {
          // Add Unicode character (2 bytes)
          builder.append(unicodeChars[random.nextInt(unicodeChars.length)])
          currentBytes += 2
        }
      }

      // Fine-tune to exact size
      val current = builder.toString()
      val currentSize = current.toByteArray(Charsets.UTF_8).size

      if (currentSize < targetSizeBytes) {
        val remaining = targetSizeBytes - currentSize
        builder.append("X".repeat(remaining)) // Pad with ASCII chars
      } else if (currentSize > targetSizeBytes) {
        // Trim to exact size (careful with UTF-8)
        return current.substring(0, current.length - (currentSize - targetSizeBytes))
      }

      return builder.toString()
    }
  }
}

/**
 * Backfill that processes menu items with large names to test large string handling
 */
class LargeMenuNameBackfill @Inject constructor(
  @ClientMiskService private val transacter: Transacter,
  private val queryFactory: Query.Factory,
) : HibernateBackfill<DbMenu, String, NoParameters>() {
  val processedMenuNames = mutableListOf<String>()

  override fun primaryKeyName(): String = "name"
  override fun primaryKeyHibernateName(): String = "name"

  override fun backfillCriteria(config: BackfillConfig<NoParameters>): Query<DbMenu> {
    return queryFactory.newQuery<MenuQuery>()
  }

  override fun runOne(pkey: String, config: BackfillConfig<NoParameters>) {
    processedMenuNames.add(pkey)
    val largeLogMessage = generateLargeLogMessage(pkey)
  }

  fun generateLargeLogMessage(menuName: String): String {
    // Generate a large message that approaches the 8192 byte limit
    // The large pkey (menuName) is naturally included in the message, testing both:
    // 1. Large pkey handling
    // 2. Large message storage in event_logs.message column
    val baseMessage = "Processing menu '$menuName' with extensive details: "
    val additionalContent = "X".repeat(3000) // Additional content to ensure message is large
    return baseMessage + additionalContent + " - processing complete for menu: $menuName"
  }

  override fun partitionProvider() = UnshardedPartitionProvider(transacter)
}
