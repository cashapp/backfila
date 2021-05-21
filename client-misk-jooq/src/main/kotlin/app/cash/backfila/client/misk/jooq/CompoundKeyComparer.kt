package com.squareup.backfila.client.base.jooq;

import java.util.List;
import javax.annotation.Nullable;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record;

/**
 * Responsible for building JOOQ conditions when comparing compound key values.
 */
public class CompoundKeyComparer {
  private final List<Field<?>> compoundKeyFields;

  /**
   * Constructs a comparer for the given compound key fields.
   *
   * @param compoundKeyFields the list of fields that make up the compound key, in order.
   */
  public CompoundKeyComparer(List<Field<?>> compoundKeyFields) {
    this.compoundKeyFields = compoundKeyFields;
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using greater than (>) semantics.
   *
   * Some examples will make it clear how this works.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   *   f1 > v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   *   f1 > v1 OR (f1 = v1 AND f2 > v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   *   f1 > v1 OR (f1 = v1 AND f2 > v2) OR (f1 = v1 AND f2 = v2 AND f3 > v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  public Condition gt(Record compoundKeyValue) {
    return buildCondition(compoundKeyValue, Field::greaterThan);
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using greater than or equal to (>=) semantics.
   *
   * Some examples will make it clear how this works.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   *   f1 >= v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   *   f1 > v1 OR (f1 = v1 AND f2 >= v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   *   f1 > v1 OR (f1 = v1 AND f2 > v2) OR (f1 = v1 AND f2 = v2 AND f3 >= v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  public Condition gte(Record compoundKeyValue) {
    return buildCondition(compoundKeyValue, Field::greaterThan, Field::greaterOrEqual);
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using less than (<) semantics.
   *
   * Some examples will make it clear how this works.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   *   f1 < v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   *   f1 < v1 OR (f1 = v1 AND f2 < v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   *   f1 < v1 OR (f1 = v1 AND f2 < v2) OR (f1 = v1 AND f2 = v2 AND f3 < v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  public Condition lt(Record compoundKeyValue) {
    return buildCondition(compoundKeyValue, Field::lessThan);
  }

  /**
   * Builds JOOQ conditions for comparing compound key values using less than or equal to (<=) semantics.
   *
   * For a single-field (f1) comparison against a single value (v1), this produces:
   *   f1 <= v1
   *
   * For a double-field (f1, f2) comparison against a compound value (v1, v2), this produces:
   *   f1 < v1 OR (f1 = v1 AND f2 <= v2)
   *
   * For a triple-field (f1, f2, f3) comparison against a compound value (v1, v2, v3), this produces:
   *   f1 < v1 OR (f1 = v1 AND f2 < v2) OR (f1 = v1 AND f2 = v2 AND f3 <= v3)
   *
   * @param compoundKeyValue JOOQ record containing the field values.
   * @return JOOQ condition
   */
  public Condition lte(Record compoundKeyValue) {
    return buildCondition(compoundKeyValue, Field::lessThan, Field::lessOrEqual);
  }

  private Condition buildCondition(Record compoundKeyValue, CompareField comparison) {
    return buildCondition(compoundKeyValue, comparison, comparison);
  }

  private Condition buildCondition(Record compoundKeyValue,
      CompareField mainComparison,
      CompareField lastFieldComparison) {
    @Nullable Condition overallCondition = null;
    @Nullable Condition priorFieldsEqualCondition = null;

    for (int i = 0; i < compoundKeyFields.size(); i++) {
      Field<?> field = compoundKeyFields.get(i);
      CompareField comparison = i == compoundKeyFields.size() - 1 ?
          lastFieldComparison :
          mainComparison;

      overallCondition = or(overallCondition,
          and(priorFieldsEqualCondition,
              compareField(field, compoundKeyValue, comparison)));

      priorFieldsEqualCondition = and(priorFieldsEqualCondition,
          compareField(field, compoundKeyValue, Field::eq));
    }

    return overallCondition;
  }

  /**
   * Compares a single field against a single value using a comparison operator.
   *
   * Defined as a separate method to allow the Java generic system to match up the T types.
   */
  private static <T> Condition compareField(
      Field<T> field,
      Record compoundKeyValue,
      CompareField fieldComparison) {
    return fieldComparison.compare(field, compoundKeyValue.get(field));
  }

  /**
   * Like {@link Condition#and(Condition)} but tolerates a null condition so we don't have to
   * pollute our generated SQL with an "always true" condition.
   */
  private static Condition and(@Nullable Condition c1, Condition c2) {
    if (c1 == null) return c2;
    return c1.and(c2);
  }

  /**
   * Like {@link Condition#or(Condition)} but tolerates a null condition so we don't have to
   * pollute our generated SQL with an "always false" condition.
   */
  private static Condition or(@Nullable Condition c1, Condition c2) {
    if (c1 == null) return c2;
    return c1.or(c2);
  }

  @FunctionalInterface private interface CompareField {
    <T> Condition compare(Field<T> field, T value);
  }
}
