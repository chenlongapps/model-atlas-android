package io.github.chenlongapps.modelatlas.data.serialization

import java.math.BigDecimal
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/** 保留 B 单位的十进制原值，不经过 Double 舍入，也不换算为虚构精确的参数个数。 */
internal object ParameterCountSerializer : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("modelatlas.ParameterCount", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): BigDecimal {
        val value = (decoder as JsonDecoder).decodeJsonElement()
        require(value is JsonPrimitive && !value.isString)
        return value.content.toBigDecimal().also(::validateParameterCount)
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: BigDecimal) {
        validateParameterCount(value)
        // JsonPrimitive(Number) 在 JSON 编码时可能转成 Double；受校验的数字字面量才能无损往返。
        (encoder as JsonEncoder).encodeJsonElement(JsonUnquotedLiteral(value.toString()))
    }
}

internal fun validateParameterCount(value: BigDecimal) {
    // 与发布方有限正数的范围一致，也避免极端指数在展示为普通小数时膨胀。
    require(value.signum() > 0 && value.toDouble().let { it.isFinite() && it > 0 })
}
