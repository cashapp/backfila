package app.cash.backfila.client.jooq.config

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.jooq.BackfillBatch
import app.cash.backfila.client.jooq.BackfillJooqTransacter
import app.cash.backfila.client.jooq.ByteStringSerializer
import app.cash.backfila.client.jooq.ByteStringSerializer.Companion.forByteArray
import app.cash.backfila.client.jooq.ByteStringSerializer.Companion.forString
import app.cash.backfila.client.jooq.JooqBackfill
import app.cash.backfila.client.jooq.gen.tables.records.WidgetsRecord
import app.cash.backfila.client.jooq.gen.tables.references.WIDGETS
import com.google.common.base.CharMatcher
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Record
import org.jooq.TableLike
import javax.inject.Inject

/**
 * Ideally you wouldn't need to override toString equals and hashcode, but since there is a ByteArray
 * field, the compiler throws a warning if you don't override it.
 */
data class CompoundKey(
  val manufacturerToken: String,
  val createdAtMs: Long,
  val widgetToken: ByteArray
) {
  fun toRecord(): Record {
    val record = WidgetsRecord()
    record.manufacturerToken = manufacturerToken
    record.createdAtMs = createdAtMs
    record.widgetToken = widgetToken
    return record
  }

  fun encode(): String {
    return "$manufacturerToken:" +
      "${zeroPad(createdAtMs, 16)}:" +
      forString.fromByteString(forByteArray.toByteString(widgetToken))
  }

  private fun zeroPad(number: Long, width: Int): String {
    return String.format("%0" + width + "d", number)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CompoundKey

    if (manufacturerToken != other.manufacturerToken) return false
    if (createdAtMs != other.createdAtMs) return false
    if (!widgetToken.contentEquals(other.widgetToken)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = manufacturerToken.hashCode()
    result = 31 * result + createdAtMs.hashCode()
    result = 31 * result + widgetToken.contentHashCode()
    return result
  }

  // Some of the `UnshardedBackfillTest` tests compare our key type's `toString()` to the
  // string value exposed by the backfila batch objects, so we need to override this so
  // our `toString()` matches the encoded version of the value.
  override fun toString(): String {
    return encode()
  }

  companion object {
    fun recordToKey(record: Record): CompoundKey {
      return CompoundKey(
        manufacturerToken = record.get(WIDGETS.MANUFACTURER_TOKEN)!!,
        createdAtMs = record.get(WIDGETS.CREATED_AT_MS)!!,
        widgetToken = record.get(WIDGETS.WIDGET_TOKEN)!!
      )
    }
    fun decode(encodedString: String): CompoundKey {
      val parts = encodedString.split(":")
      check(parts.size == 3) { "expected exactly 3 encoded parts" }
      val createdAtMsWithZerosStripped = CharMatcher.`is`('0').trimLeadingFrom(parts[1])
      return CompoundKey(
        manufacturerToken = parts[0],
        createdAtMs = createdAtMsWithZerosStripped.toLong(),
        widgetToken = forByteArray.fromByteString(forString.toByteString(parts[2])),
      )
    }
  }
}

data class WidgetParametersBackfill(
  @Description("manufacturer tokens separated by commas with no spaces in between")
  val manufacturerTokensCSV: String = "token1 - select for backfill,token3 - select for backfill"
)

@Description("So we can backfill widgets.")
class JooqWidgetCompoundKeyBackfill @Inject constructor(
  @JooqDBIdentifier private val jooqTransacter: JooqTransacter
) : JooqBackfill<CompoundKey, WidgetParametersBackfill>(),
  IdRecorder<CompoundKey, WidgetParametersBackfill> {
  override val idsRanDry = mutableListOf<CompoundKey>()
  override val idsRanWet = mutableListOf<CompoundKey>()

  override val shardedTransacterMap: Map<String, BackfillJooqTransacter>
    get() = mapOf("unsharded" to jooqTransacter)

  override val table: TableLike<*>
    get() = WIDGETS

  override val compoundKeyFields: List<Field<*>>
    get() = listOf(WIDGETS.MANUFACTURER_TOKEN, WIDGETS.CREATED_AT_MS, WIDGETS.WIDGET_TOKEN)

  override fun keyToRecord(key: CompoundKey): Record {
    return key.toRecord()
  }

  override fun filterCondition(config: BackfillConfig<WidgetParametersBackfill>): Condition {
    return WIDGETS.MANUFACTURER_TOKEN.`in`(
      config.parameters.manufacturerTokensCSV.split(",")
    )
  }
  override fun recordToKey(record: Record): CompoundKey {
    return CompoundKey.recordToKey(record)
  }

  override val keySerializer: ByteStringSerializer<CompoundKey>
    get() = forString.composeWith(
      CompoundKey::encode,
      CompoundKey.Companion::decode
    )

  override fun backfill(backfillBatch: BackfillBatch<CompoundKey, WidgetParametersBackfill>) {
    if (backfillBatch.config.dryRun) {
      idsRanDry.addAll(backfillBatch.keys)
    } else {
      idsRanWet.addAll(backfillBatch.keys)
    }
  }
}
