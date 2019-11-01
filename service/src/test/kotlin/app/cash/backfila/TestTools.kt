package app.cash.backfila

import misk.MiskCaller
import misk.inject.keyOf
import misk.scope.ActionScope

fun <T> ActionScope.fakeCaller(
  service: String? = null,
  user: String? = null,
  function: () -> T
): T {
  return enter(mapOf(keyOf<MiskCaller>() to MiskCaller(service = service, user = user)))
      .use { function() }
}
