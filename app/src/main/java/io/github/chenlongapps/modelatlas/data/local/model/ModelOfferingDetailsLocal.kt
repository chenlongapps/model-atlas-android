package io.github.chenlongapps.modelatlas.data.local.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** 类型与可选值保持开放；values 中的 null 不擅自解释为关闭。 */
@Serializable
data class ReasoningOptionLocal(
    val type: String,
    val min: Long? = null,
    val max: Long? = null,
    val values: List<String?>? = null,
)

/** 仅记录目录中的请求覆盖配置；body 和 headers 不由本地存储层执行。 */
@Serializable
data class ProviderOverrideLocal(
    val npmPackage: String? = null,
    val apiUrl: String? = null,
    val shape: String? = null,
    val body: JsonObject? = null,
    val headers: Map<String, String>? = null,
)

/** 模式报价与基础报价分开保存，不自动合并价格或请求参数。 */
@Serializable
data class ExperimentalModeLocal(
    val pricing: OfferingPricingLocal? = null,
    val providerOverride: ProviderOverrideLocal? = null,
)
