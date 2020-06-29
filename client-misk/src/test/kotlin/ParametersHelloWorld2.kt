

/**
 * LBP -> List of parameter names, maybe types
 * LBP.member -> ByteString
 * ByteString -> LBP.member
 */
/**
class BackfilaParametersOperator2<T : Any>(
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

abstract class TheBackfill2 {
  init {
    val kType = this::class.qualifiedName + "${'$'}Parameters"
    val parametersType = Class.forName(kType)
    operator = BackfilaParametersOperator2<K>((kType as Class<*>).kotlin as KClass<K>)
  }

  fun <T> getParameters(): T {

  }

  fun initParameter(map: Map<String, ByteString>) {
    parameters = operator.mapToDataClass(map)
  }
}
class LunchBackfill2 : TheBackfill2<LunchBackfill2.Parameters>() {
  fun foo() {
    println(getParameters())
  }

  data class Parameters(
    val drink: String = "water",
    val sandwich: String = "peanut butter and jelly"
  )
}

fun main() {
  val backfill = object : TheBackfill2<LunchBackfillParameters2>() {
  }
  backfill.doParameters(LunchBackfillParameters2(drink = "pepsi"))
  println(backfill.operator.mapToDataClass(mapOf("sandwich" to "\"BLT\"")))
} **/
