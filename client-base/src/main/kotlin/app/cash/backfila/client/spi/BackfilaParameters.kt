package app.cash.backfila.client.spi

import app.cash.backfila.client.BackfilaDefault
import app.cash.backfila.client.BackfilaRequired
import app.cash.backfila.client.BackfillConfig
import app.cash.backfila.client.Description
import app.cash.backfila.client.PrepareBackfillConfig
import app.cash.backfila.protos.clientservice.GetNextBatchRangeRequest
import app.cash.backfila.protos.clientservice.PrepareBackfillRequest
import app.cash.backfila.protos.clientservice.RunBatchRequest
import app.cash.backfila.protos.service.Parameter
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

fun parametersToBytes(parameters: Any): Map<String, ByteString> {
  val parametersClass = parameters::class

  val map = mutableMapOf<String, ByteString>()

  for (property in parametersClass.memberProperties) {
    val value = property.getter.call(parameters)
    if (value != null) {
      map[property.name] = value.toString().encodeUtf8()
    }
  }
  return map
}

class BackfilaParametersOperator<T : Any>(
  val parametersClass: KClass<T>,
) {
  /** Constructor parameters used as defaults when missing to create a new T. */
  private val constructor: KFunction<T> = fetchConstructor(parametersClass)

  fun constructBackfillConfig(request: PrepareBackfillRequest): PrepareBackfillConfig<T> =
    PrepareBackfillConfig(
      constructParameters(request.parameters),
      request.dry_run,
    )

  fun constructBackfillConfig(request: GetNextBatchRangeRequest): BackfillConfig<T> = BackfillConfig(
    constructParameters(request.parameters),
    request.partition_name,
    request.backfill_id,
    request.dry_run,
  )

  fun constructBackfillConfig(request: RunBatchRequest): BackfillConfig<T> = BackfillConfig(
    constructParameters(request.parameters),
    request.partition_name,
    request.backfill_id,
    request.dry_run,
  )

  fun constructParameters(
    parameters: MutableMap<String, ByteString>,
  ): T {
    val map = mutableMapOf<KParameter, Any>()
    for (parameter in constructor.parameters) {
      if (parameters.containsKey(parameter.name)) {
        val value = parameters[parameter.name]!!
        map[parameter] = TYPE_CONVERTERS[parameter.type.jvmErasure]!!.invoke(value)
      } else {
        val requiredAnnotation = parameter.findAnnotation<BackfilaRequired>()
        if (requiredAnnotation != null) {
          require(parameters.containsKey(requiredAnnotation.name)) {
            "Parameter data class has a required member ${requiredAnnotation.name} with no provided value."
          }
          val value = parameters[requiredAnnotation.name]!!
          map[parameter] = TYPE_CONVERTERS[parameter.type.jvmErasure]!!.invoke(value)
        }
        val defaultAnnotation = parameter.findAnnotation<BackfilaDefault>()
        if (defaultAnnotation != null) {
          if (parameters.containsKey(defaultAnnotation.name)) {
            val value = parameters[defaultAnnotation.name]!!
            map[parameter] = TYPE_CONVERTERS[parameter.type.jvmErasure]!!.invoke(value)
          } else {
            val defaultValue = defaultAnnotation.value
            map[parameter] = TYPE_CONVERTERS[parameter.type.jvmErasure]!!.invoke(defaultValue.encodeUtf8())
          }
        }
      }
    }
    try {
      return constructor.callBy(map)
    } catch (e: InvocationTargetException) {
      throw IllegalArgumentException("Failed to create Parameter object $parametersClass", e.cause)
    }
  }

  companion object {
    val TYPE_CONVERTERS = mapOf(
      String::class to { value: ByteString -> value.utf8() },
      Int::class to { value: ByteString -> value.utf8().toInt() },
      Long::class to { value: ByteString -> value.utf8().toLong() },
      Boolean::class to { value: ByteString -> value.utf8().toBoolean() },
    )

    fun <P : Any> backfilaParametersFromClass(parametersClass: KClass<P>): List<Parameter> {
      // Validate that we can handle the parameters if they are specified.
      for (parameter in fetchConstructor(parametersClass).parameters) {
        check(parameter.type.jvmErasure in TYPE_CONVERTERS.keys) {
          "Parameter data class has member $parameter with unhandled type ${parameter.type.jvmErasure}"
        }
      }
      return fetchConstructor(parametersClass).parameters.map {
        val description = it.findAnnotation<Description>()?.text
        // For Java we use BackfilaDefault since the name is arg0... otherwise.
        val defaultAnnotation = it.findAnnotation<BackfilaDefault>()
        val name = defaultAnnotation?.name ?: it.name
        Parameter.Builder()
          .name(name)
          .description(description)
          .build()
      }
    }

    private fun <P : Any> fetchConstructor(parametersClass: KClass<P>): KFunction<P> {
      if (parametersClass.primaryConstructor != null) {
        return parametersClass.primaryConstructor!!
      }
      check(parametersClass.constructors.size == 1) {
        "Only one constructor is allowed for java parameter classes."
      }
      return parametersClass.constructors.single()
    }
  }
}
