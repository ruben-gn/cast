package cast.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.*
import java.io.File

@OptIn(ExperimentalSerializationApi::class)
fun main() {
    val classLoader = Thread.currentThread().contextClassLoader
    val packageUrl = classLoader.getResource("cast/api") ?: error("cast/api not on classpath")

    val serializers = File(packageUrl.toURI())
        .listFiles { f -> f.extension == "class" && '$' !in f.name }
        .orEmpty()
        .sortedBy { it.name }
        .mapNotNull { file ->
            val className = "cast.api.${file.nameWithoutExtension}"
            runCatching {
                val klass = Class.forName(className, true, classLoader)
                val companion = klass.getDeclaredField("Companion").get(null)
                val serializer = companion.javaClass.getMethod("serializer").invoke(companion) as KSerializer<*>
                file.nameWithoutExtension to serializer
            }.getOrNull()
        }

    println("// Generated from Kotlin shared-models -- do not edit manually")
    println()
    for ((name, serializer) in serializers) {
        // Sealed parents describe themselves as a {type, value} envelope, which says nothing useful
        // in TypeScript; their subclasses are emitted on their own as flat interfaces.
        if (serializer.descriptor.kind is PolymorphicKind) continue
        println(generateInterface(name, serializer.descriptor))
    }
}

private fun generateInterface(name: String, descriptor: SerialDescriptor): String = buildString {
    appendLine("export interface $name {")
    // A @SerialName that isn't just the class's own FQN means the class is a member of a sealed
    // hierarchy, where that name is the value of the `type` discriminator rather than a field.
    if (descriptor.serialName != "cast.api.$name")
        appendLine("  type: '${descriptor.serialName}'")
    for (i in 0 until descriptor.elementsCount) {
        val fieldName = descriptor.getElementName(i)
        val tsType = toTypeScriptType(descriptor.getElementDescriptor(i))
        appendLine("  $fieldName: $tsType")
    }
    append("}")
}

private fun toTypeScriptType(descriptor: SerialDescriptor): String {
    val base = when (descriptor.kind) {
        PrimitiveKind.STRING -> "string"
        PrimitiveKind.BOOLEAN -> "boolean"
        PrimitiveKind.LONG, PrimitiveKind.INT, PrimitiveKind.SHORT, PrimitiveKind.BYTE,
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> "number"
        StructureKind.LIST -> "${toTypeScriptType(descriptor.getElementDescriptor(0))}[]"
        StructureKind.MAP -> "Record<string, ${toTypeScriptType(descriptor.getElementDescriptor(1))}>"
        StructureKind.CLASS -> descriptor.serialName.substringAfterLast('.')
        SerialKind.ENUM -> "string"
        else -> "unknown"
    }
    return if (descriptor.isNullable) "$base | null" else base
}
