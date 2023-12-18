package app.cash.backfila

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.wire.GrpcCall
import com.squareup.wire.kotlin.KotlinGenerator
import com.squareup.wire.schema.Extend
import com.squareup.wire.schema.Field
import com.squareup.wire.schema.ProtoFile
import com.squareup.wire.schema.Rpc
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaHandler
import com.squareup.wire.schema.Service
import com.squareup.wire.schema.Type
import okio.Path
import okio.Path.Companion.toPath
import java.lang.IllegalArgumentException

class FakeGrpcClientGenerator : SchemaHandler() {
  private lateinit var kotlinGenerator: KotlinGenerator

  override fun handle(schema: Schema, context: Context) {
    kotlinGenerator = KotlinGenerator(schema)

    schema.protoFiles.forEach { protoFile: ProtoFile ->
      protoFile.services.forEach { service: Service ->
        val file = generateFileSpec(service)

        // ensure the directory for the file is created
        val fileDirectoryPath = context.outDirectory / service.packageName.toFilePath()
        context.fileSystem.createDirectories(fileDirectoryPath)

        // write the file
        context.fileSystem.write(fileDirectoryPath / "${service.fakeClassName}.kt") {
          writeUtf8(file.toString())
        }
      }
    }
  }

  private fun generateFileSpec(service: Service): FileSpec {
    val (type, imports) = generateTypeSpecAndImports(service)

    return FileSpec.builder(service.packageName, service.fakeClassName)
      .apply { imports.forEach { addImport(it.packageName, it.simpleName) } }
      .addType(type)
      .build()
  }

  override fun handle(extend: Extend, field: Field, context: Context): Path? = null
  override fun handle(service: Service, context: Context): List<Path> = listOf()
  override fun handle(type: Type, context: Context): Path? = null

  private fun generateTypeSpecAndImports(service: Service): Pair<TypeSpec, Set<ClassName>> {
    val classes = mutableSetOf(
      ClassName.bestGuess(GrpcCall::class.java.canonicalName)
    )

    val fakeGrpcClientClass = ClassName(service.packageName, service.fakeClassName)
    val clientInterface = kotlinGenerator.generatedServiceName(service)
    classes.add(clientInterface)

    val typeSpec = TypeSpec.classBuilder(fakeGrpcClientClass).apply {
      addSuperinterface(clientInterface)

      val returnTypes = kotlinGenerator.generateServiceTypeSpecs(service)
        .get(clientInterface)
        ?.funSpecs
        ?.associate { it.name to it.returnType as ParameterizedTypeName }
        ?: throw IllegalArgumentException("service ${service.name} should be wire-generated")

      service.rpcs.forEach { rpc: Rpc ->
        val fakeCallFactoryName = rpc.name.decapitalize()
        val implReturnType = returnTypes[rpc.name]
          ?: throw IllegalArgumentException("function ${rpc.name} should be wire-generated")

        val (requestType, responseType) = implReturnType
          .run { typeArguments as List<ClassName> }
          .also { require(it.size == 2) { "return type should have 2 parameterized types" } }
          .let { it.first() to it.last() }

        classes.add(requestType)
        classes.add(responseType)

        val fakeCallFactoryType = ClassName("app.cash.backfila.client", "FakeGrpcCallFactory")
          .parameterizedBy(requestType, responseType)

        // build the fake call factory
        addProperty(
          PropertySpec.Companion.builder(fakeCallFactoryName, fakeCallFactoryType)
            .initializer(CodeBlock.of("FakeGrpcCallFactory()"))
            .build()
        )

        // implement the gRPC method
        addFunction(
          FunSpec.builder(rpc.name)
            .addModifiers(KModifier.OVERRIDE)
            .returns(returnTypes[rpc.name]!!)
            .addStatement("return $fakeCallFactoryName.call()")
            .build()
        )
      }
    }.build()

    return typeSpec to classes
  }

  private val Service.packageName get() = kotlinGenerator.generatedServiceName(this).packageName
  private val Service.fakeClassName get() = "Fake${name}GrpcClient"

  private fun String.decapitalize() = first().lowercase() + substring(1)
  private fun String.toFilePath() = replace(".", "/").toPath()

  class Factory : SchemaHandler.Factory {
    override fun create(): SchemaHandler {
      return FakeGrpcClientGenerator()
    }
  }
}
