package io.github.chenlongapps.modelatlas.ui.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel

/** 仅暂留本次主动取消的来源卡片，不延迟持久化，也不冻结整个收藏列表。 */
internal class FavoriteRemovalTransition {
    /** 收藏 Screen 注册即时读取，写入前同步捕获，避免列表 Flow 先于成功回调删除来源。 */
    var source: ((String) -> RetainedFavorite?)? = null
    var retained by mutableStateOf<RetainedFavorite?>(null)
        private set
    var awaitingReturn by mutableStateOf(false)
        private set
    var pinnedId by mutableStateOf<String?>(null)
        private set
    var pinnedIndex by mutableStateOf(0)
        private set

    fun pin(id: String, index: Int) {
        pinnedId = id
        pinnedIndex = index
    }

    fun begin(id: String) {
        retained = source?.invoke(id)
        awaitingReturn = retained != null
    }

    fun finishReturn() {
        awaitingReturn = false
        pinnedId = null
    }

    fun clear(id: String) {
        if (retained?.favorite?.identity?.id != id) return
        retained = null
        awaitingReturn = false
    }

    fun displayFavorites(favorites: List<FavoriteModel>): List<FavoriteModel> {
        val entry = retained ?: return favorites
        if (favorites.any { it.identity.id == entry.favorite.identity.id }) return favorites
        return favorites.toMutableList().apply { add(entry.index.coerceIn(0, size), entry.favorite) }
    }
}

internal data class RetainedFavorite(val favorite: FavoriteModel, val index: Int)

internal val LocalFavoriteRemovalTransition = staticCompositionLocalOf<FavoriteRemovalTransition?> { null }
