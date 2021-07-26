package app.cash.backfila

import app.cash.backfila.dashboard.UiEventLog
import app.cash.backfila.service.persistence.DbEventLog
import misk.MiskCaller
import misk.inject.keyOf
import misk.scope.ActionScope
import org.assertj.core.api.Assertions
import org.assertj.core.api.Condition
import org.assertj.core.api.SoftAssertions

fun <T> ActionScope.fakeCaller(
  service: String? = null,
  user: String? = null,
  function: () -> T
): T {
  return enter(mapOf(keyOf<MiskCaller>() to MiskCaller(service = service, user = user)))
    .use { function() }
}

// For useful soft assertions without forgetting to call assertAll()
fun softAssert(assertions: SoftAssertions.() -> Unit) {
  with(SoftAssertions()) {
    assertions()
    assertAll()
  }
}

// Makes asserting on UiEventLog entries easy.
fun uiEventLogWith(
  type: DbEventLog.Type,
  user: String?,
  message: String
): Condition<UiEventLog> {
  return Assertions.allOf(
    Condition({ it.type == type }, "uiEventLog.type"),
    Condition({ it.user == user }, "uiEventLog.user"),
    Condition({ it.message == message }, "uiEventLog.message")
  )
}
