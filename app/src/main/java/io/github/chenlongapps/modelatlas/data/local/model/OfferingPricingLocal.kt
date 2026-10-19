package io.github.chenlongapps.modelatlas.data.local.model

import kotlinx.serialization.Serializable

/**
 * 完整保存单个服务或模式的目录报价，不换算、不选择档位，也不用于财务结算。
 *
 * LLM 报价口径为 USD / 百万 tokens；null 不等于零，推理价不能默认与输出价相加。
 * [tiers] 保留精确条件；[contextOver200k] 只是旧版兼容信息，不能据其名称推断门槛或重复计价。
 */
@Serializable
data class OfferingPricingLocal(
    val input: Double? = null,
    val output: Double? = null,
    val cacheRead: Double? = null,
    val cacheWrite: Double? = null,
    val inputAudio: Double? = null,
    val outputAudio: Double? = null,
    val reasoning: Double? = null,
    val tiers: List<OfferingPriceTierLocal>? = null,
    val contextOver200k: OfferingPricingLocal? = null,
)

@Serializable
data class OfferingPriceTierLocal(
    val condition: PriceTierConditionLocal,
    val input: Double? = null,
    val output: Double? = null,
    val cacheRead: Double? = null,
    val cacheWrite: Double? = null,
    val inputAudio: Double? = null,
    val outputAudio: Double? = null,
    val reasoning: Double? = null,
)

/** 保留原始条件类型与门槛，不解释门槛等号归属或累进计费规则。 */
@Serializable
data class PriceTierConditionLocal(
    val type: String,
    val size: Long,
)
