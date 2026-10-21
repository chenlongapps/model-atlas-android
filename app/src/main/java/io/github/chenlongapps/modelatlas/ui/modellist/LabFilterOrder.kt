package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.ModelSummary

// 前八个 LAB 固定优先，并非实时热度或模型能力排名；与报价服务商优先级无关。
private val LabFilterPriority = listOf(
    "openai",
    "anthropic",
    "google",
    "deepseek",
    "alibaba",
    "meta",
    "xai",
    "moonshotai",
).withIndex().associate { (index, lab) -> lab to index }

/** 按完整目录计数，不随当前搜索或筛选条件改变。目录中的模型已按稳定 ID 去重。 */
internal fun countModelsByLab(models: List<ModelSummary>): Map<String, Int> =
    models.mapNotNull { it.lab }.groupingBy { it }.eachCount()

/** 前八个固定优先，其余按模型数量降序，同数量按原始 ID 字母序稳定兜底。 */
internal fun sortFilterLabs(labs: Iterable<String>, modelCounts: Map<String, Int>): List<String> = labs.sortedWith(
    compareBy<String> { LabFilterPriority[it] ?: Int.MAX_VALUE }
        .thenByDescending { modelCounts[it] ?: 0 }
        .thenBy { it },
)
