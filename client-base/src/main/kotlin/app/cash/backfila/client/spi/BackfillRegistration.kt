package app.cash.backfila.client.spi

import kotlin.reflect.KClass

data class BackfillRegistration(
  val name: String,
  val description: String?,
  val parametersClass: KClass<Any>
)
