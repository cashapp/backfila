package app.cash.backfila.ui.actions

import app.cash.backfila.ui.actions.BackfillCreateHandlerAction.Companion.MAX_FORM_BODY_BYTES
import app.cash.backfila.ui.actions.BackfillCreateHandlerAction.Companion.readBoundedFormBody
import misk.exceptions.BadRequestException
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BackfillCreateHandlerActionTest {
  @Test
  fun `accepts a form body at the size limit`() {
    val body = Buffer().writeUtf8("a".repeat(MAX_FORM_BODY_BYTES))

    assertThat(readBoundedFormBody(body)).hasSize(MAX_FORM_BODY_BYTES)
  }

  @Test
  fun `accepts several fully encoded parameters at the value size limit`() {
    val encodedValue = "%7B".repeat(4096)
    val body = Buffer().writeUtf8((1..6).joinToString("&") { "customParameter_$it=$encodedValue" })

    assertThat(readBoundedFormBody(body)).contains("customParameter_6=$encodedValue")
  }

  @Test
  fun `rejects a form body above the size limit`() {
    val body = Buffer().writeUtf8("a".repeat(MAX_FORM_BODY_BYTES + 1))

    assertThatThrownBy { readBoundedFormBody(body) }
      .isInstanceOf(BadRequestException::class.java)
      .hasMessage("submitted form is too large (max $MAX_FORM_BODY_BYTES encoded bytes)")
  }
}
