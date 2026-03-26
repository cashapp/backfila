package app.cash.backfila.client.spi

import java.time.Instant
import kotlin.reflect.KClass

data class BackfillRegistration
@JvmOverloads
constructor(
  val name: String,
  val description: String?,
  val parametersClass: KClass<out Any>,
  val deleteBy: Instant?,
  val unit: String? = null,
)
