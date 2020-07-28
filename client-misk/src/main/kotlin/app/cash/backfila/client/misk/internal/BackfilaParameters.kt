package app.cash.backfila.client.misk.internal

import app.cash.backfila.client.misk.Backfill
import app.cash.backfila.client.misk.BackfillConfig
import app.cash.backfila.protos.service.Parameter
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

fun parametersToBytes(parameters: Any): Map<String, ByteString> {
  val parametersClass = parameters::class
  val map = mutableMapOf<String, ByteString>()

  for (property in parametersClass.memberProperties) {
    map[property.name] = (property.getter.call(parameters) as String).encodeUtf8()
  }
  return map
}

private fun <T : Any> parametersClass(backfillClass: KClass<out Backfill<*, *, T>>): KClass<T> {
  // Like MyBackfill.
  val thisType = TypeLiteral.get(backfillClass.java)

  // Like Backfill<E, Id<E>, MyDataClass>.
  val supertype = thisType.getSupertype(Backfill::class.java).type as ParameterizedType

  // Like MyDataClass
  return (Types.getRawType(supertype.actualTypeArguments[2]) as Class<T>).kotlin
}

internal class BackfilaParametersOperator<T : Any>(
  backfillClass: KClass<out Backfill<*, *, T>>
) {
  val parametersClass: KClass<T> = parametersClass(backfillClass)

  /** Constructor parameters to create a new T. */
  val constructorParameters: List<KParameter>

  init {
    constructorParameters = parametersClass.primaryConstructor!!.parameters
    // TODO check parameters
  }

  fun constructBackfillConfig(
    parameters: MutableMap<String, ByteString>,
    dryRun: Boolean
  ): BackfillConfig<T> {
    val map = mutableMapOf<KParameter, Any>()
    for (parameter in constructorParameters) {
      if (parameters.containsKey(parameter.name)) {
        val value = parameters[parameter.name]!!
        map[parameter] = TYPE_CONVERTERS.getValue(parameter.type.jvmErasure).invoke(value)
      }
    }
    val instance = parametersClass.primaryConstructor!!.callBy(map)
    return BackfillConfig(instance, dryRun)
  }

  companion object {
    val TYPE_CONVERTERS = mapOf(
        String::class to { value: ByteString -> value.utf8() },
        Int::class to { value: ByteString -> value.utf8().toInt() }
    )

    internal inline fun <reified T : Any> backfilaParametersForBackfill(backfillClass: KClass<Backfill<*, *, T>>): List<Parameter> {
      val parametersClass = parametersClass(backfillClass)

      // Validate that we can handle the parameters if they are specified.
      for (parameter in parametersClass.primaryConstructor!!.parameters) {
        check(parameter.type.jvmErasure in TYPE_CONVERTERS.keys) {
          "Parameter data class has member $parameter with unhandled type ${parameter.type.jvmErasure}"
        }
      }
      return parametersClass.primaryConstructor?.parameters?.map {
        Parameter.Builder().name(it.name).build()
      } ?: emptyList()
    }
  }
}
