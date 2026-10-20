package io.github.chenlongapps.modelatlas.data.model

/**
 * 独立列表摘要模型。
 *
 * [openWeights] 是来源报告的训练权重公开状态，不等于开源、允许任意用途或允许商用；
 * [license] 保留原始许可字符串，仅在权重公开且许可名称简短时随列表展示，具体许可以来源文本为准。
 * [price] 是从多个服务商报价中选出的单条展示报价，详见
 * [io.github.chenlongapps.modelatlas.data.mapper.selectPriceFor]；为 null 表示暂无可用报价。
 */
data class ModelSummary(
    val id: String,
    val name: String,
    val releaseDate: String? = null,
    val contextLimitTokens: Long? = null,
    val outputLimitTokens: Long? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
    val openWeights: Boolean? = null,
    val license: String? = null,
    val price: ModelPriceSummary? = null,
) {
    /** 列表约定的 ID 前缀标签，不代表经独立核实的开发商。 */
    val lab: String?
        get() = id.substringBefore('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
}
