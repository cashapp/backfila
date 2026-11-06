package app.cash.backfila.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

class FormValuesTest {
  @ParameterizedTest(name = "parseCheckbox({0}) is unset")
  @NullAndEmptySource
  @ValueSource(strings = [" "])
  fun `parseCheckbox treats missing or blank values as unset`(value: String?) {
    assertThat(parseCheckbox(value)).isNull()
  }

  @ParameterizedTest(name = "parseCheckbox({0}) is unchecked")
  @ValueSource(strings = ["off", "OFF", "false", " False "])
  fun `parseCheckbox treats off and false as unchecked in any case`(value: String) {
    assertThat(parseCheckbox(value)).isFalse()
  }

  @ParameterizedTest(name = "parseCheckbox({0}) is checked")
  @ValueSource(strings = ["on", "true", "1"])
  fun `parseCheckbox treats any other value as checked`(value: String) {
    assertThat(parseCheckbox(value)).isTrue()
  }
}
