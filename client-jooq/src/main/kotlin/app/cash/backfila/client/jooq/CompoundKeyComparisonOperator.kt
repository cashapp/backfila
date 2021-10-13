package app.cash.backfila.client.jooq

import org.jooq.Condition
import org.jooq.Record

/**
 * A function that builds a jooq condition using a {@link CompoundKeyComparer} and a jooq
 * {@link Record} (which represents the compound key value). Usually specified using a method
 * reference such as `CompoundKeyComparer::gt` or `CompoundKeyComparer::gte`.
 */
typealias CompoundKeyComparisonOperator<T> = (
  comparer: CompoundKeyComparer<T>,
  record: Record
) -> Condition
