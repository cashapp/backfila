package app.cash.backfila.client.jooq

import org.jooq.Condition
import org.jooq.Field
import org.jooq.Record

/**
 * Responsible for building JOOQ conditions when comparing compound key values.
 * @param compoundKeyFields the list of fields that make up the compound key, in order.
 */
class CompoundKeyComparer<T>(private val compoundKeyFields: List<Field<*>>) {
  /**
   * Builds JOOQ conditions for comparing compound key values using greater than (>) semantics.
   *
   * Some examples will make it clear how this works.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   * f1 > v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   * f1 > v1 OR (f1 = v1 AND f2 > v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   * f1 > v1 OR (f1 = v1 AND f2 > v2) OR (f1 = v1 AND f2 = v2 AND f3 > v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  fun gt(compoundKeyValue: Record): Condition {
    return buildCondition(
      compoundKeyValue
    ) { obj: Field<T>, value: T -> obj.greaterThan(value) }
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using greater than or equal to (>=) semantics.
   *
   * Some examples will make it clear how this works.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   * f1 >= v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   * f1 > v1 OR (f1 = v1 AND f2 >= v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   * f1 > v1 OR (f1 = v1 AND f2 > v2) OR (f1 = v1 AND f2 = v2 AND f3 >= v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  fun gte(compoundKeyValue: Record): Condition {
    return buildCondition(
      compoundKeyValue,
      { obj: Field<T>, value: T -> obj.greaterThan(value) },
      { obj: Field<T>, value: T -> obj.greaterOrEqual(value) }
    )
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using less than (<) semantics.
   *
   * Some examples will make it clear how this works.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   * f1 < v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   * f1 < v1 OR (f1 = v1 AND f2 < v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   * f1 < v1 OR (f1 = v1 AND f2 < v2) OR (f1 = v1 AND f2 = v2 AND f3 < v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  fun lt(compoundKeyValue: Record): Condition {
    return buildCondition(
      compoundKeyValue
    ) { obj: Field<T>, value: T -> obj.lessThan(value) }
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using less than or equal to (<=) semantics.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   * f1 <= v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   * f1 < v1 OR (f1 = v1 AND f2 <= v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   * f1 < v1 OR (f1 = v1 AND f2 < v2) OR (f1 = v1 AND f2 = v2 AND f3 <= v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  fun lte(compoundKeyValue: Record): Condition {
    return buildCondition(
      compoundKeyValue,
      { obj: Field<T>, value: T -> obj.lessThan(value) },
      { obj: Field<T>, value: T -> obj.lessOrEqual(value) }
    )
  }

  private fun buildCondition(
    compoundKeyValue: Record,
    comparison: (field: Field<T>, value: T) -> Condition
  ): Condition {
    return buildCondition(compoundKeyValue, comparison, comparison)
  }

  private fun buildCondition(
    compoundKeyValue: Record,
    mainComparison: (field: Field<T>, value: T) -> Condition,
    lastFieldComparison: (field: Field<T>, value: T) -> Condition
  ): Condition {
    var overallCondition: Condition? = null
    var priorFieldsEqualCondition: Condition? = null
    for (i in compoundKeyFields.indices) {
      val field = compoundKeyFields[i]
      val comparison = if (i == compoundKeyFields.size - 1) lastFieldComparison else mainComparison
      overallCondition = or(
        overallCondition,
        and(
          priorFieldsEqualCondition,
          @Suppress("UNCHECKED_CAST")
          comparison(field as Field<T>, compoundKeyValue.get(field))
        )
      )
      priorFieldsEqualCondition = and(
        priorFieldsEqualCondition,
        field.eq(compoundKeyValue.get(field))
      )
    }
    return overallCondition ?: throw IllegalStateException("overall condition cannot be null")
  }

  companion object {
    /**
     * Like [Condition.and] but tolerates a null condition so we don't have to
     * pollute our generated SQL with an "always true" condition.
     */
    private fun and(c1: Condition?, c2: Condition): Condition {
      return c1?.and(c2) ?: c2
    }

    /**
     * Like [Condition.or] but tolerates a null condition so we don't have to
     * pollute our generated SQL with an "always false" condition.
     */
    private fun or(c1: Condition?, c2: Condition): Condition {
      return c1?.or(c2) ?: c2
    }
  }
}
