package io.github.chenlongapps.modelatlas.data.local.converter

import androidx.room3.ColumnTypeConverter
import io.github.chenlongapps.modelatlas.data.local.model.ExperimentalModeLocal
import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelLinkLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelWeightLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.local.model.ProviderOverrideLocal
import io.github.chenlongapps.modelatlas.data.local.model.ReasoningOptionLocal
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 将本地复杂字段存为 JSON TEXT，null 写为 SQL NULL，与空集合或空对象区分。
 *
 * 未知对象字段允许忽略；损坏的 JSON 或已知字段类型错误直接抛出序列化异常，不伪造业务默认值。
 */
class CatalogTypeConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @ColumnTypeConverter
    fun stringListToJson(value: List<String>?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToStringList(value: String?): List<String>? = decode(value)

    @ColumnTypeConverter
    fun weightsToJson(value: List<ModelWeightLocal>?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToWeights(value: String?): List<ModelWeightLocal>? = decode(value)

    @ColumnTypeConverter
    fun linksToJson(value: List<ModelLinkLocal>?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToLinks(value: String?): List<ModelLinkLocal>? = decode(value)

    @ColumnTypeConverter
    fun reasoningOptionsToJson(value: List<ReasoningOptionLocal>?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToReasoningOptions(value: String?): List<ReasoningOptionLocal>? = decode(value)

    @ColumnTypeConverter
    fun interleavedToJson(value: InterleavedLocal?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToInterleaved(value: String?): InterleavedLocal? = decode(value)

    @ColumnTypeConverter
    fun pricingToJson(value: OfferingPricingLocal?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToPricing(value: String?): OfferingPricingLocal? = decode(value)

    @ColumnTypeConverter
    fun providerOverrideToJson(value: ProviderOverrideLocal?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToProviderOverride(value: String?): ProviderOverrideLocal? = decode(value)

    @ColumnTypeConverter
    fun experimentalModesToJson(value: Map<String, ExperimentalModeLocal>?): String? = encode(value)

    @ColumnTypeConverter
    fun jsonToExperimentalModes(value: String?): Map<String, ExperimentalModeLocal>? = decode(value)

    private inline fun <reified T> encode(value: T?): String? =
        value?.let { json.encodeToString(it) }

    private inline fun <reified T> decode(value: String?): T? =
        value?.let { json.decodeFromString<T?>(it) }
}
