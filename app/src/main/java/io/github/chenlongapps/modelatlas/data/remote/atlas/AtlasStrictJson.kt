package io.github.chenlongapps.modelatlas.data.remote.atlas

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.serializer

/** Kotlinx 默认会接受带引号的 boolean/number；已知类型必须与发布 JSON 一致。 */
internal inline fun <reified T> decodeAtlasJson(bytes: ByteArray): T {
    val element = AtlasJson.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true))
    validateAtlasTypes(element, serializer<T>().descriptor)
    return AtlasJson.decodeFromJsonElement<T>(element)
}

@OptIn(ExperimentalSerializationApi::class)
internal fun validateAtlasTypes(element: JsonElement, descriptor: SerialDescriptor) {
    if (element == JsonNull) {
        require(descriptor.isNullable)
        return
    }
    if (descriptor.serialName.startsWith("kotlinx.serialization.json.")) return
    when (descriptor.kind) {
        PrimitiveKind.STRING -> require(element is JsonPrimitive && element.isString)
        PrimitiveKind.BOOLEAN -> require(element is JsonPrimitive && !element.isString && element.booleanOrNull != null)
        is PrimitiveKind -> require(element is JsonPrimitive && !element.isString)
        StructureKind.LIST -> {
            require(element is JsonArray)
            element.forEach { validateAtlasTypes(it, descriptor.getElementDescriptor(0)) }
        }
        StructureKind.MAP -> {
            require(element is JsonObject)
            element.values.forEach { validateAtlasTypes(it, descriptor.getElementDescriptor(1)) }
        }
        StructureKind.CLASS, StructureKind.OBJECT -> {
            require(element is JsonObject)
            element.forEach { (key, value) ->
                val index = descriptor.getElementIndex(key)
                if (index >= 0) validateAtlasTypes(value, descriptor.getElementDescriptor(index))
            }
        }
        else -> Unit
    }
}
