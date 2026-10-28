package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.ModelSummary

internal data class CatalogEntry(val id: String, val model: ModelSummary?, val favorite: FavoriteModel?)

/** 目录顺序不变，收藏仅作稳定前置；下架条目不猜测未知参数。 */
internal fun catalogEntries(models: List<ModelSummary>, favorites: List<FavoriteModel>, query: ModelListQuery): List<CatalogEntry> {
    val ids = favorites.associateBy { it.identity.id }
    val availableIds = models.mapTo(mutableSetOf()) { it.id }
    val filtered = filterModels(models, query)
    val (saved, others) = filtered.partition { it.id in ids }
    val unavailable = favorites.filter { favorite ->
        val identity = favorite.identity
        favorite.model == null && identity.id !in availableIds &&
            (query.labs.isEmpty() || identity.lab in query.labs) &&
            query.weights == WeightFilter.ALL && query.inputModalities.isEmpty() && query.outputModalities.isEmpty() &&
            query.search.trim().split(Regex("\\s+")).filter(String::isNotEmpty).all { term ->
                identity.name.contains(term, true) || identity.id.contains(term, true) || identity.lab?.contains(term, true) == true
            }
    }.map { CatalogEntry(it.identity.id, null, it) }
    return saved.map { CatalogEntry(it.id, it, ids[it.id]) } + unavailable +
        others.map { CatalogEntry(it.id, it, null) }
}
