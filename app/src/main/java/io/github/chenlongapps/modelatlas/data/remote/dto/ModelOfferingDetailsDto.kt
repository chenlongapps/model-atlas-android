package io.github.chenlongapps.modelatlas.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ReasoningOptionDto(
    val type: String,
    val min: Long? = null,
    val max: Long? = null,
    val values: List<String?>? = null,
)

/** 保留响应中的原始价格数值；缺失价格与明确的零价不同。 */
@Serializable
data class CostDto(
    val input: Double? = null,
    val output: Double? = null,
    @SerialName("cache_read") val cacheRead: Double? = null,
    @SerialName("cache_write") val cacheWrite: Double? = null,
    @SerialName("input_audio") val inputAudio: Double? = null,
    @SerialName("output_audio") val outputAudio: Double? = null,
    val reasoning: Double? = null,
    val tiers: List<CostTierDto>? = null,
    @SerialName("context_over_200k") val contextOver200k: CostDto? = null,
)

@Serializable
data class CostTierDto(
    val tier: CostTierConditionDto,
    val input: Double? = null,
    val output: Double? = null,
    @SerialName("cache_read") val cacheRead: Double? = null,
    @SerialName("cache_write") val cacheWrite: Double? = null,
    @SerialName("input_audio") val inputAudio: Double? = null,
    @SerialName("output_audio") val outputAudio: Double? = null,
    val reasoning: Double? = null,
)

@Serializable
data class CostTierConditionDto(
    val type: String,
    val size: Long,
)

/** body 是服务商自定义的请求参数，保留任意 JSON 键和值，不限定为当前已知参数。 */
@Serializable
data class ProviderOverrideDto(
    val npm: String? = null,
    val api: String? = null,
    val shape: String? = null,
    val body: JsonObject? = null,
    val headers: Map<String, String>? = null,
)

@Serializable
data class ExperimentalDto(
    val modes: Map<String, ExperimentalModeDto>? = null,
)

@Serializable
data class ExperimentalModeDto(
    val cost: CostDto? = null,
    val provider: ProviderOverrideDto? = null,
)
