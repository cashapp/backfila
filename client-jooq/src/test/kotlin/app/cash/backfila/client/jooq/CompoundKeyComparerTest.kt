package app.cash.backfila.client.jooq

import app.cash.backfila.client.jooq.gen.tables.references.WIDGETS
import com.google.common.collect.ImmutableList
import org.assertj.core.api.Assertions.assertThat
import org.jooq.Condition
import org.jooq.Field
import org.jooq.Record
import org.jooq.SQLDialect
import org.jooq.impl.DefaultDSLContext
import org.junit.jupiter.api.Test

class CompoundKeyComparerTest {
  @Test
  fun gtWithOneField() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.gt(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1"
      )
    )
    assertThat(sqlCondition).isEqualTo("${WIDGETS.NAME} > 'w1'")
  }

  @Test
  fun gtWithTwoFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.gt(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.NAME} > 'w1' OR (${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} > 10))"
    )
  }

  @Test
  fun gtWithThreeFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.gt(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.MANUFACTURER_TOKEN, "m1",
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.MANUFACTURER_TOKEN} > 'm1' OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} > 'w1') OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} > 10))"
    )
  }

  @Test
  fun gteWithOneField() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.gte(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1"
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "${WIDGETS.NAME} >= 'w1'",
    )
  }

  @Test
  fun gteWithTwoFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.gte(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.NAME} > 'w1' OR (${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} >= 10))",
    )
  }

  @Test
  fun gteWithThreeFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.gte(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.MANUFACTURER_TOKEN, "m1",
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.MANUFACTURER_TOKEN} > 'm1' OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} > 'w1') OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} >= 10))",
    )
  }

  @Test
  fun ltWithOneField() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.lt(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1"
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "${WIDGETS.NAME} < 'w1'",
    )
  }

  @Test
  fun ltWithTwoFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.lt(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.NAME} < 'w1' OR (${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} < 10))",
    )
  }

  @Test
  fun ltWithThreeFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.lt(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.MANUFACTURER_TOKEN, "m1",
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.MANUFACTURER_TOKEN} < 'm1' OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} < 'w1') OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} < 10))",
    )
  }

  @Test
  fun lteWithOneField() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.lte(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1"
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "${WIDGETS.NAME} <= 'w1'",
    )
  }

  @Test
  fun lteWithTwoFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.lte(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.NAME} < 'w1' OR (${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} <= 10))",
    )
  }

  @Test
  fun lteWithThreeFields() {
    val sqlCondition = compare(
      { obj: CompoundKeyComparer<*>, compoundKeyValue: Record? ->
        obj.lte(
          compoundKeyValue!!
        )
      },
      newRecord(
        WIDGETS.MANUFACTURER_TOKEN, "m1",
        WIDGETS.NAME, "w1",
        WIDGETS.CREATED_AT_MS, 10L
      )
    )
    assertThat(sqlCondition).isEqualTo(
      "(${WIDGETS.MANUFACTURER_TOKEN} < 'm1' OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} < 'w1') OR " +
        "(${WIDGETS.MANUFACTURER_TOKEN} = 'm1' AND ${WIDGETS.NAME} = 'w1' AND ${WIDGETS.CREATED_AT_MS} <= 10))",
    )
  }

  private fun <F1> newRecord(field1: Field<F1>, value1: F1): Record {
    return recordFactory.newRecord(field1)
      .with(field1, value1)
  }

  private fun <F1, F2> newRecord(
    field1: Field<F1>,
    value1: F1,
    field2: Field<F2>,
    value2: F2
  ): Record {
    return recordFactory.newRecord(field1, field2)
      .with(field1, value1)
      .with(field2, value2)
  }

  private fun <F1, F2, F3> newRecord(
    field1: Field<F1>,
    value1: F1,
    field2: Field<F2>,
    value2: F2,
    field3: Field<F3>,
    value3: F3
  ): Record {
    return recordFactory.newRecord(field1, field2, field3)
      .with(field1, value1)
      .with(field2, value2)
      .with(field3, value3)
  }

  companion object {
    private val recordFactory = DefaultDSLContext(SQLDialect.MYSQL)
    private fun compare(
      compareLambda: (compoundKeyComparer: CompoundKeyComparer<*>, record: Record) -> Condition,
      record: Record
    ): String {
      val comparer: CompoundKeyComparer<*> =
        CompoundKeyComparer<Any?>(ImmutableList.copyOf(record.fields()))
      val condition = compareLambda(comparer, record)

      // Here we normalize the SQL string to make it easy to assert on.
      //   - Replace multiple whitespace characters with a single space.
      //   - Remove excess spaces from within parens
      //   - Uppercase AND/OR
      return condition.toString()
        .replace("\\s+".toRegex(), " ")
        .replace(" or ".toRegex(), " OR ")
        .replace(" and ".toRegex(), " AND ")
        .replace("\\( ".toRegex(), "(")
        .replace(" \\)".toRegex(), ")")
    }
  }
}
