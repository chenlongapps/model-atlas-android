package io.github.chenlongapps.modelatlas.data.model

data class FavoriteIdentity(val id: String, val name: String, val lab: String?)

/** 不可用时只有历史身份，不提供旧参数或旧报价。 */
data class FavoriteModel(
    val identity: FavoriteIdentity,
    val favoritedAtEpochMillis: Long,
    val model: ModelSummary?,
)
