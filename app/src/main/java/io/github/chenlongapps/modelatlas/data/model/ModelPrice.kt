package io.github.chenlongapps.modelatlas.data.model

/**
 * 列表展示的单条模型报价摘要。
 *
 * 报价来自某个 API 服务商对该规范模型的模型服务，不是无条件通用价格，也不代表该服务商一定可用。
 * 所有单价口径为 **USD / 百万 tokens**：null 表示暂无数据，不等于零；明确的 `0` 只表示该报价字段为零。
 * [isOfficial] 表示该服务商是否由 [PriceProviderPolicy] 认定为模型开发方的第一方服务，
 * 不是价格最低、服务最快或经过本应用验证的标志。
 */
data class ModelPriceSummary(
    val providerId: String,
    val providerName: String,
    val isOfficial: Boolean,
    val input: Double? = null,
    val output: Double? = null,
    val cacheRead: Double? = null,
    val cacheWrite: Double? = null,
    val inputAudio: Double? = null,
    val outputAudio: Double? = null,
    val reasoning: Double? = null,
    val tiers: List<ModelPriceTierSummary> = emptyList(),
)

/**
 * 单个条件档位。[conditionType] 与 [conditionSizeTokens] 保留来源原值，
 * 这里只按 `context` 类型展示上下文门槛，不解释门槛等号归属或累进计费规则。
 */
data class ModelPriceTierSummary(
    val conditionType: String,
    val conditionSizeTokens: Long,
    val input: Double? = null,
    val output: Double? = null,
    val cacheRead: Double? = null,
    val cacheWrite: Double? = null,
)
