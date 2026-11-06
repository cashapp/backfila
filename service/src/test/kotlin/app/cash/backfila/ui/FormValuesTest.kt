package app.cash.backfila.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormValuesTest {
  @Test
  fun `parseCheckbox treats missing or blank values as unset`() {
    assertThat(parseCheckbox(null)).isNull()
    assertThat(parseCheckbox("")).isNull()
    assertThat(parseCheckbox(" ")).isNull()
  }

  @Test
  fun `parseCheckbox treats off and false as unchecked in any case`() {
    assertThat(parseCheckbox("off")).isFalse()
    assertThat(parseCheckbox("OFF")).isFalse()
    assertThat(parseCheckbox("false")).isFalse()
    assertThat(parseCheckbox(" False ")).isFalse()
  }

  @Test
  fun `parseCheckbox treats any other value as checked`() {
    assertThat(parseCheckbox("on")).isTrue()
    assertThat(parseCheckbox("true")).isTrue()
    assertThat(parseCheckbox("1")).isTrue()
  }
}
