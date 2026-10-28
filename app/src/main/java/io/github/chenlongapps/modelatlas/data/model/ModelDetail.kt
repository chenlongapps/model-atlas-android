package io.github.chenlongapps.modelatlas.data.model

/** 详情专用应用模型，不向 UI 暴露 DTO、Entity 或本地 JSON 配置。 */
data class ModelDetail(
    val id: String,
    val name: String,
    val metadata: ModelDetailMetadata = ModelDetailMetadata(),
    val license: String? = null,
    val weights: List<ModelWeight>? = null,
    val links: List<ModelLink>? = null,
    val source: String = "models.dev",
    val fetchedAtEpochMillis: Long,
    val providers: List<ModelDetailProvider> = emptyList(),
    val benchmarks: List<ModelBenchmark>? = null,
) {
    val lab: String?
        get() = id.substringBefore('/', missingDelimiterValue = "").takeIf(String::isNotBlank)
}

/** 规范模型和服务各自拥有一份元数据，绝不互相补齐限制或能力。 */
data class ModelDetailMetadata(
    val description: String? = null,
    val family: String? = null,
    val knowledgeCutoff: String? = null,
    val releaseDate: String? = null,
    val sourceLastUpdated: String? = null,
    val attachment: Boolean? = null,
    val reasoning: Boolean? = null,
    val toolCall: Boolean? = null,
    val structuredOutput: Boolean? = null,
    val temperature: Boolean? = null,
    val openWeights: Boolean? = null,
    val contextLimitTokens: Long? = null,
    val inputLimitTokens: Long? = null,
    val outputLimitTokens: Long? = null,
    val inputModalities: List<String>? = null,
    val outputModalities: List<String>? = null,
)

data class ModelWeight(val url: String, val label: String?, val quantization: String?, val format: String?)
data class ModelLink(val url: String, val label: String?, val type: String?)

/** 来源报告的评测原值，不推断分数方向、百分比或跨配置可比性。 */
data class ModelBenchmark(
    val name: String,
    val score: Double,
    val metric: String? = null,
    val source: String? = null,
    val date: String? = null,
    val version: String? = null,
    val variant: String? = null,
    val dataset: String? = null,
    val harness: String? = null,
    val unit: String? = null,
    val higherIsBetter: Boolean? = null,
    val reasoningEffort: String? = null,
    val toolsEnabled: Boolean? = null,
    val sourceType: String? = null,
)

data class ModelDetailProvider(
    val id: String,
    val name: String,
    val documentationUrl: String?,
    val offerings: List<ModelDetailOffering>,
)

data class ModelDetailOffering(
    val id: String,
    val name: String,
    val metadata: ModelDetailMetadata,
    val status: String?,
    val price: ModelDetailPrice?,
    val modes: Map<String, ModelDetailPrice?>?,
    val reasoningOptions: List<ModelReasoningOption>?,
    val interleaved: ModelInterleaved?,
    val source: String,
    val fetchedAtEpochMillis: Long,
)

data class ModelReasoningOption(val type: String, val min: Long?, val max: Long?, val values: List<String?>?)

data class ModelInterleaved(val supported: Boolean? = null, val field: String? = null)

/** 所有类别按原值独立展示；不合计推理价、不从旧 context_over_200k 猜门槛。 */
data class ModelDetailPrice(
    val input: Double? = null,
    val output: Double? = null,
    val cacheRead: Double? = null,
    val cacheWrite: Double? = null,
    val reasoning: Double? = null,
    val inputAudio: Double? = null,
    val outputAudio: Double? = null,
    val tiers: List<ModelDetailPriceTier>? = null,
)

data class ModelDetailPriceTier(val type: String, val size: Long, val price: ModelDetailPrice)
