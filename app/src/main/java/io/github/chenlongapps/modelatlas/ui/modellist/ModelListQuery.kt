package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import kotlinx.serialization.Serializable

/** 仅针对列表已具备的规范模型字段；不推测缺失能力或合并服务商限制。 */
@Serializable
data class ModelListQuery(
    val search: String = "",
    val labs: Set<String> = emptySet(),
    val weights: WeightFilter = WeightFilter.ALL,
    val inputModalities: Set<String> = emptySet(),
    val outputModalities: Set<String> = emptySet(),
    val minContextTokens: Long? = null,
    val minOutputTokens: Long? = null,
    val hasPrice: Boolean? = null,
) {
    // LAB 多选是一组 OR 条件；各模态是独立的必需条件。
    val filterCount: Int
        get() = (if (labs.isEmpty()) 0 else 1) +
            (if (weights == WeightFilter.ALL) 0 else 1) +
            inputModalities.size + outputModalities.size +
            (if (minContextTokens == null) 0 else 1) +
            (if (minOutputTokens == null) 0 else 1) +
            (if (hasPrice == null) 0 else 1)

    val isActive: Boolean get() = search.isNotBlank() || filterCount > 0

    fun resetFilters() = ModelListQuery(search = search)
}

@Serializable
enum class WeightFilter { ALL, OPEN, CLOSED, UNKNOWN }

/** 保留仓库的原始顺序。搜索词之间为 AND，每个词可匹配名称、稳定 ID 或 LAB。 */
internal fun filterModels(models: List<ModelSummary>, query: ModelListQuery): List<ModelSummary> {
    if (!query.isActive) return models
    val terms = query.search.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    return models.filter { model ->
        terms.all { term ->
            model.name.contains(term, ignoreCase = true) ||
                model.id.contains(term, ignoreCase = true) ||
                model.lab?.contains(term, ignoreCase = true) == true
        } &&
            (query.labs.isEmpty() || model.lab in query.labs) &&
            when (query.weights) {
                WeightFilter.ALL -> true
                WeightFilter.OPEN -> model.openWeights == true
                WeightFilter.CLOSED -> model.openWeights == false
                WeightFilter.UNKNOWN -> model.openWeights == null
            } &&
            query.inputModalities.all { it in model.inputModalities.orEmpty() } &&
            query.outputModalities.all { it in model.outputModalities.orEmpty() } &&
            (query.minContextTokens?.let { min -> model.contextLimitTokens?.let { it >= min } ?: false } ?: true) &&
            (query.minOutputTokens?.let { min -> model.outputLimitTokens?.let { it >= min } ?: false } ?: true) &&
            (query.hasPrice?.let { (model.price != null) == it } ?: true)
    }
}
