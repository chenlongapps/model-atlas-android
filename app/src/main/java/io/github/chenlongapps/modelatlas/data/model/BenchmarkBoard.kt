package io.github.chenlongapps.modelatlas.data.model

/** 已明确匹配定义的评测；不同版本、指标及单位各自成榜。 */
data class BenchmarkBoard(
    val id: String,
    val benchmarkId: String,
    val name: String,
    val version: String,
    val metric: String,
    val unit: String?,
    val category: String?,
    val description: String?,
    val higherIsBetter: Boolean?,
    val minScore: Double?,
    val maxScore: Double?,
    val rankings: List<BenchmarkBoardEntry> = emptyList(),
)

data class BenchmarkBoardEntry(
    val scoreId: String,
    val modelId: String,
    val modelName: String,
    val lab: String?,
    val score: Double?,
    val scoreText: String?,
    val rank: Int? = null,
    val reasoningEffort: String? = null,
    val variant: String? = null,
    val harness: String? = null,
    val dataset: String? = null,
    val toolsEnabled: Boolean? = null,
)
