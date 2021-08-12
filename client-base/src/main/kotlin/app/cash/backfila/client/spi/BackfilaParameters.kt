package app.cash.backfila.client.spi

import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.protos.service.Parameter
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import kotlin.reflect.full.findAnnotation

fun parametersToBytes(parameters: Any): Map<String, ByteString> {
  val parametersClass = parameters::class

  val map = mutableMapOf<String, ByteString>()

  for (property in parametersClass.memberProperties) {
    map[property.name] = (property.getter.call(parameters).toString()).encodeUtf8()
  }
  return map
}

class BackfilaParametersOperator<T : Any>(
  val parametersClass: KClass<T>
) {
  /** Constructor parameters used as defaults when missing to create a new T. */
  private val constructorParameters: List<KParameter> = parametersClass.primaryConstructor!!.parameters

  fun constructBackfillConfig(
    parameters: MutableMap<String, ByteString>,
    dryRun: Boolean
  ): BackfillConfig<T> {
    val map = mutableMapOf<KParameter, Any>()
    for (parameter in constructorParameters) {
      if (parameters.containsKey(parameter.name)) {
        val value = parameters[parameter.name]!!
        map[parameter] = TYPE_CONVERTERS[parameter.type.jvmErasure]!!.invoke(value)
      }
    }
    val instance = parametersClass.primaryConstructor!!.callBy(map)
    return BackfillConfig(instance, dryRun)
  }

  companion object {
    val TYPE_CONVERTERS = mapOf(
      String::class to { value: ByteString -> value.utf8() },
      Int::class to { value: ByteString -> value.utf8().toInt() },
      Long::class to { value: ByteString -> value.utf8().toLong() },
      Boolean::class to { value: ByteString -> value.utf8().toBoolean() }
    )

    inline fun <reified P : Any> backfilaParametersFromClass(parametersClass: KClass<P>): List<Parameter> {
      // Validate that we can handle the parameters if they are specified.
      for (parameter in parametersClass.primaryConstructor!!.parameters) {
        check(parameter.type.jvmErasure in TYPE_CONVERTERS.keys) {
          "Parameter data class has member $parameter with unhandled type ${parameter.type.jvmErasure}"
        }
      }
      return parametersClass.primaryConstructor?.parameters?.map {
        val description = it.findAnnotation<Description>()?.text
        Parameter.Builder()
          .name(it.name)
          .description(description)
          .build()
      } ?: emptyList()
    }
  }
}
