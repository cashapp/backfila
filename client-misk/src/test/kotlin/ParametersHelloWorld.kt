import com.google.inject.TypeLiteral
import java.lang.reflect.ParameterizedType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * LBP -> List of parameter names, maybe types
 * LBP.member -> ByteString
 * ByteString -> LBP.member
 */

class BackfilaParametersOperator<T : Any>(
  val parametersDataClass: KClass<T>
) {
  /** Constructor paramters to create a new T. */
  val parameters: List<KParameter>

  /** Getters to create a new map. */
  val properties: List<KProperty<*>>

  init {
    parameters = parametersDataClass.primaryConstructor!!.parameters
    properties = parameters.map { parameter ->
      parametersDataClass.memberProperties.first { it.name == parameter.name }
    }
  }

  fun dataClassToMap(t: T): Map<String, ByteString> {
    val map = mutableMapOf<String, ByteString>()
    for (property in properties) {
      map[property.name] = (property.getter.call(t) as String).encodeUtf8()
    }
    return map
  }

  fun mapToDataClass(map: Map<String, ByteString?>): T {
    val args = mutableMapOf<KParameter, String>()
    for (parameter in parameters) {
      val value = map[parameter.name] ?: continue
      args[parameter] = value.utf8()
    }
    return parametersDataClass.primaryConstructor!!.callBy(args)
  }

  fun parameterNames(): List<String> {
    return parameters.map { it.name!! }
  }
}

data class LunchBackfillParameters(
  val drink: String = "water",
  val sandwich: String = "peanut butter and jelly"
)

abstract class TheBackfill<K : Any> {
  val operator: BackfilaParametersOperator<K>

  init {
    val thisType = TypeLiteral.get(this::class.java).getSupertype(TheBackfill::class.java)
    val kType = (thisType.type as ParameterizedType).actualTypeArguments[0]
    operator = BackfilaParametersOperator<K>((kType as Class<*>).kotlin as KClass<K>)
  }

  lateinit var parameters: K

  fun doParameters(parameters: K) {
    println(operator.dataClassToMap(parameters))
    println(operator.parameterNames())
  }
}

fun main() {
  val backfill = object : TheBackfill<LunchBackfillParameters>() {
  }
  backfill.doParameters(LunchBackfillParameters(drink = "pepsi"))
  println(backfill.operator.mapToDataClass(mapOf("sandwich" to "BLT".encodeUtf8())))
}
