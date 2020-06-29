package app.cash.backfila.client.testing3

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
import misk.inject.typeLiteral
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * LBP -> List of parameter names, maybe types
 * LBP.member -> ByteString
 * ByteString -> LBP.member
 */

class BackfilaParametersOperatorBuilder(
  val backfillKClass: KClass<out Backfill>
) {
  val operator: BackfilaParametersOperator<BackfillParameters>

  init {
    val kClass = backfillKClass
    val parameterDataMember = kClass.members.first { member -> member.name == "parameterData" }

    val parameterDataType = parameterDataMember.returnType

    val typeLiteral = parameterDataType.javaType.typeLiteral()

    typeLiteral.type

    val kotlin = parameterDataType.javaClass.kotlin as KClass<BackfillParameters>

    operator = BackfilaParametersOperator((typeLiteral.type as Class<*>).kotlin as KClass<BackfillParameters>)
  }
}

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
) : BackfillParameters()

abstract class Backfill {
  open val parameterData: BackfillParameters = BackfillParameters()

  fun doParameters() {
    println(parameterData)
    doBatch()
  }

  abstract fun doBatch()
}

open class BackfillParameters

class LunchBackfill(
  override val parameterData: LunchBackfillParameters
) : Backfill() {
  constructor(parameterData: BackfillParameters) : this(parameterData = parameterData as LunchBackfillParameters) // Do we need this?

  override fun doBatch() {
    println("do things with $parameterData")
  }
}

fun main() {

  val fromBackfila = mapOf("sandwich" to "BLT".encodeUtf8())

  val builder = BackfilaParametersOperatorBuilder(LunchBackfill::class)

  println(builder.operator.mapToDataClass(fromBackfila))

  val parametersDataClass = builder.operator.parametersDataClass

  val backfill = LunchBackfill(builder.operator.mapToDataClass(fromBackfila)) // we have parameterDataClass, how do we cast the data class to the actual class so we don't need that secondary constructor? Maybe this is impossible but that seems obnoxious. Or does it all come down to type erasure?
  backfill.doParameters()
}
