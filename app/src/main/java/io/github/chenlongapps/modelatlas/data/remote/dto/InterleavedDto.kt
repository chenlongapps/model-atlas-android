package io.github.chenlongapps.modelatlas.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** interleaved 在响应中既可能是布尔值，也可能是包含 field 的对象。 */
@Serializable(with = InterleavedDtoSerializer::class)
sealed interface InterleavedDto {
    data class Toggle(val enabled: Boolean) : InterleavedDto

    @Serializable
    data class Field(val field: String) : InterleavedDto
}

object InterleavedDtoSerializer : KSerializer<InterleavedDto> {
    override val descriptor = buildClassSerialDescriptor("InterleavedDto")

    override fun deserialize(decoder: Decoder): InterleavedDto {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("InterleavedDto only supports JSON")

        return when (val value = jsonDecoder.decodeJsonElement()) {
            is JsonObject -> jsonDecoder.json.decodeFromJsonElement(
                InterleavedDto.Field.serializer(),
                value,
            )
            is JsonPrimitive -> {
                val enabled = if (value.isString) null else value.booleanOrNull
                enabled?.let { InterleavedDto.Toggle(it) }
                    ?: throw SerializationException("interleaved must be a boolean or a field object")
            }
            else -> throw SerializationException("interleaved must be a boolean or a field object")
        }
    }

    override fun serialize(encoder: Encoder, value: InterleavedDto) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("InterleavedDto only supports JSON")

        val element = when (value) {
            is InterleavedDto.Toggle -> JsonPrimitive(value.enabled)
            is InterleavedDto.Field -> jsonEncoder.json.encodeToJsonElement(
                InterleavedDto.Field.serializer(),
                value,
            )
        }
        jsonEncoder.encodeJsonElement(element)
    }
}
