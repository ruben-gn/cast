package cast.api

import kotlinx.serialization.descriptors.*
import kotlinx.serialization.serializer

fun main() {
    val serializers = listOf(
        serializer<EpisodeDto>(),
        serializer<PodcastSummaryDto>(),
        serializer<PodcastDetailDto>(),
        serializer<AddPodcastRequest>(),
        serializer<PlaybackStateResponse>(),
    )

    println("// Generated from Kotlin shared-models -- do not edit manually")
    println()
    for (s in serializers) {
        println(generateInterface(s.descriptor))
    }
}

private fun generateInterface(descriptor: SerialDescriptor): String = buildString {
    val name = descriptor.serialName.substringAfterLast('.')
    appendLine("export interface $name {")
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
