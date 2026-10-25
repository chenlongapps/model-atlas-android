package io.github.chenlongapps.modelatlas.ui.modelcompare

import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListQuery
import io.github.chenlongapps.modelatlas.ui.modellist.filterModels

/** 仅重排当前目录中的搜索结果；两组内部保留目录原有顺序。 */
internal fun filterCompareModels(models: List<ModelSummary>, favoriteIds: Set<String>, search: String): List<ModelSummary> {
    val (favorites, others) = filterModels(models, ModelListQuery(search = search)).partition { it.id in favoriteIds }
    return favorites + others
}
