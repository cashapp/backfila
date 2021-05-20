package app.cash.backfila.client.misk.jooq

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.protos.clientservice.KeyRange
import com.google.common.base.Preconditions
import com.squareup.backfila.client.base.jooq.ByteStringSerializer
import com.squareup.backfila.client.base.jooq.CompoundKeyComparer
import com.squareup.backfila.client.base.jooq.CompoundKeyComparisonOperator
import okio.ByteString
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SortField
import org.jooq.TableLike

/**
 * Implement this interface in your project to run a backfill with Jooq
 *
 * @param <K> the type of key being backfilled over
 * @param <Param> backfill parameters
*/
interface JooqBackfill<K, Param: Any>: Backfill {
  /**
   * A map of jooq transacters, indexed by shard database name, used to interact with the
   * database(s). For the unsharded case, this will will be a map of one entry.
   */
  val shardedTransacterMapBackfill: Map<String, BackfillJooqTransacter>

  /**
   * The table containing the rows the backfill will run over.
   */
  val table: TableLike<*>

  /**
   * Jooq condition that limits which rows the backfill runs over.
   */
  val filterCondition: Condition

  /**
   * List of fields that uniquely identifies keys to backfill.
   *
   * Note: this could be a list of a single field when you aren't using a real compound key.
   */
  val compoundKeyFields: List<Field<*>>

  /**
   * Convert from a JOOQ Record to the K type.
   */
  fun recordToKey(record: Record): K

  /**
   * Convert from the K type to a JOOQ Record.
   */
  fun keyToRecord(key: K): Record

  /**
   * A serializer for the configured key type, since the Backfila protos use [ByteString]s.
   */
  val keySerializer: ByteStringSerializer<K>

  /**
   * Hook that gives you a place to prepare or validate the backfill. Throw an exception from your
   * consumer to indicate the backfill is invalid.
   */
  fun prepareAndValidateBackfill(config: BackfillConfig<Param>) {}

  /**
   * Function that performs the work of the backfill by applying some side effect to the given
   * list of key values.
   *
   * Note: this will be called outside of a transaction.
   */
  fun backfill(backfillBatch: BackfillBatch<K, Param>)


  fun toByteString(key: K): ByteString {
    return keySerializer.toByteString(key)
  }

  fun fromByteString(byteString: ByteString?): K {
    return keySerializer.fromByteString(byteString)
  }

  fun buildKeyRange(start: K, end: K): KeyRange {
    return KeyRange.Builder()
      .start(toByteString(start))
      .end(toByteString(end))
      .build()
  }

  fun <T> inTransactionReturning(
    comment: String,
    partitionName: String?,
    work: (dslContext: DSLContext) -> T
  ): T {
    val transacterBackfill: BackfillJooqTransacter = getTransacter(partitionName)
    return transacterBackfill.transaction(comment, work)
  }

  fun sortingByCompoundKeyFields(
    withSortDirection: (field: Field<*>) -> SortField<*>
  ): List<SortField<*>> {
    return compoundKeyFields
      .map(withSortDirection)
  }

  fun getUnfilteredBoundaryKeyValue(
    partitionName: String,
    withSortDirection: (field: Field<*>) -> SortField<*>
  ): K? {
    return inTransactionReturning(
      "JooqConfig#getUnfilteredBoundaryKeyValue",
      partitionName
    ) { session: DSLContext ->
      session
        .select(compoundKeyFields)
        .from(table)
        .orderBy(sortingByCompoundKeyFields(withSortDirection))
        .limit(1)
        .fetchOptional {
          recordToKey(it)
        }.orElse(null)
    }
  }

  fun getTransacter(partitionName: String?): BackfillJooqTransacter {
    val transacterBackfill: BackfillJooqTransacter? = shardedTransacterMapBackfill[partitionName]
    Preconditions.checkArgument(
      transacterBackfill != null,
      String.format(
        "A JooqTransacter for the following partitionName was not found: %s",
        partitionName
      )
    )
    return transacterBackfill!!
  }

  fun compareCompoundKey(compoundKey: K, compare: CompoundKeyComparisonOperator): Condition {
    return compare.compare(compoundKeyComparer(), keyToRecord(compoundKey))
  }

  fun compoundKeyComparer(): CompoundKeyComparer {
    return CompoundKeyComparer(compoundKeyFields)
  }
}
