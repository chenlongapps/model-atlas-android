package io.github.chenlongapps.modelatlas.ui.modellist

import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.ModelSummary

internal data class CatalogEntry(val id: String, val model: ModelSummary?, val favorite: FavoriteModel?)

/** 收藏增删仍实时生效，目录同步带来的身份／可用性变化则跟随列表展示快照。 */
internal class CatalogFavoriteSnapshot(models: List<ModelSummary>) {
    private val modelsById = models.associateBy { it.id }
    private val identities = mutableMapOf<String, FavoriteIdentity>()

    fun project(favorites: List<FavoriteModel>): List<FavoriteModel> {
        identities.keys.retainAll(favorites.mapTo(hashSetOf()) { it.identity.id })
        return favorites.map { favorite ->
            val model = modelsById[favorite.identity.id]
            val identity = identities.getOrPut(favorite.identity.id) {
                model?.let { FavoriteIdentity(it.id, it.name, it.lab) } ?: favorite.identity
            }
            favorite.copy(identity = identity, model = model)
        }
    }
}

/** 目录顺序不变，收藏仅作稳定前置；下架条目不猜测未知参数。 */
internal fun catalogEntries(models: List<ModelSummary>, favorites: List<FavoriteModel>, query: ModelListQuery): List<CatalogEntry> {
    val ids = favorites.associateBy { it.identity.id }
    val availableIds = models.mapTo(mutableSetOf()) { it.id }
    val filtered = filterModels(models, query)
    val (saved, others) = filtered.partition { it.id in ids }
    val unavailable = favorites.filter { favorite ->
        val identity = favorite.identity
        // 可用性以本次列表快照为准，不随后台目录变化。
        identity.id !in availableIds &&
            (query.labs.isEmpty() || identity.lab in query.labs) &&
            query.weights == WeightFilter.ALL && query.inputModalities.isEmpty() && query.outputModalities.isEmpty() &&
            query.search.trim().split(Regex("\\s+")).filter(String::isNotEmpty).all { term ->
                identity.name.contains(term, true) || identity.id.contains(term, true) || identity.lab?.contains(term, true) == true
            }
    }.map { CatalogEntry(it.identity.id, null, it) }
    return saved.map { CatalogEntry(it.id, it, ids[it.id]) } + unavailable +
        others.map { CatalogEntry(it.id, it, null) }
}
