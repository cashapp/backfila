package app.cash.backfila.client.misk

import app.cash.backfila.protos.service.Parameter
import com.google.inject.TypeLiteral
import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import misk.exceptions.BadRequestException

interface Parameterized {
  /**
   * Returns this backfill's available parameters. These are sent to the Backfila service which will
   * offer fields for users to type values in for each parameter.
   *
   * This can be configured directly but you probably want [DataClassParameter]
   */
  val parameters: List<Parameter>
    get() = listOf()
}

/**
 * For native kotlin data class parameter configuration and access
 */
interface DataClassParameter<T : Any> : Parameterized {
  private fun dataClass(): KClass<T> {
    // Like MyBackfill.
    val thisType = TypeLiteral.get(this::class.java)

    // Like DataClassParamProvider<MyDataClass>.
    val supertype = thisType.getSupertype(DataClassParameter::class.java).type as ParameterizedType

    @Suppress("UNCHECKED_CAST")
    return (Types.getRawType(supertype.actualTypeArguments[0]) as Class<T>).kotlin
  }

  override val parameters: List<Parameter>
    get() {
      val dataClass = dataClass()
      // Validate that we can handle the parameters if they are specified.
      val handledTypes = listOf(String::class, Int::class)
      for (parameter in dataClass.primaryConstructor!!.parameters) {
        check(parameter.type.jvmErasure in handledTypes) {
          "Parameter data class has member $parameter with unhandled type ${parameter.type.jvmErasure}"
        }
      }
      return dataClass.memberProperties.map { Parameter.Builder().name(it.name).build() }
    }

  fun BackfillConfig.parameterData(): T {
    return parametersFromConfig(this)
  }

  fun parametersFromConfig(config: BackfillConfig): T {
    val dataClass = dataClass()
    val parameters = dataClass.primaryConstructor!!.parameters
    val map = mutableMapOf<KParameter, Any>()
    for (parameter in parameters) {
      if (config.parameters.containsKey(parameter.name)) {
        val value = config.parameters[parameter.name]!!
        when (parameter.type.jvmErasure) {
          String::class -> {
            map[parameter] = value.utf8()
          }
          Int::class -> {
            map[parameter] = value.utf8().toInt()
          }
          else -> {
            throw BadRequestException("Parameter data class has member $parameter with unhandled type ${parameter.type.jvmErasure}")
          }
        }
      }
    }

    return dataClass.primaryConstructor!!.callBy(map)
  }
}
