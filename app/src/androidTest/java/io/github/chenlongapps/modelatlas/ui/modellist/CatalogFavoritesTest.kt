package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.favorites.FavoritesUiState
import io.github.chenlongapps.modelatlas.ui.favorites.FavoriteRemovalTransition
import io.github.chenlongapps.modelatlas.ui.favorites.LocalFavoriteRemovalTransition
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CatalogFavoritesTest {
    @get:Rule val compose = createComposeRule()
    private val a = ModelSummary("lab/a", "Alpha")
    private val b = ModelSummary("lab/b", "Beta")
    private val saved = FavoriteModel(FavoriteIdentity(b.id, b.name, b.lab), 1, b)
    private val missing = FavoriteModel(FavoriteIdentity("old/gone", "Historical", "old"), 2, null)

    @Test fun favoriteMovesFirstAndUnavailableRemainsAccessible() {
        val favorites = mutableStateOf(FavoritesUiState(false, listOf(saved, missing)))
        var opened = ""
        lateinit var listState: LazyListState
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                listState = rememberLazyListState()
                ModelListScreen(ModelListUiState(models = listOf(a, b), isLoading = false),
                    favoritesState = favorites.value, onModelClick = { opened = it }, listState = listState)
            }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/b"))
        compose.onNodeWithTag("model-favorite:lab/b", useUnmergedTree = true).assertExists()
        compose.runOnIdle { assertEquals(3, listState.layoutInfo.visibleItemsInfo.single { it.key == "model:lab/b" }.index) }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("favorite-unavailable:old/gone"))
        compose.runOnIdle { assertEquals(4, listState.layoutInfo.visibleItemsInfo.single { it.key == "model:old/gone" }.index) }
        compose.onNodeWithTag("favorite-unavailable:old/gone").performClick()
        compose.runOnIdle { assertEquals("old/gone", opened) }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/a"))
        compose.runOnIdle { assertEquals(5, listState.layoutInfo.visibleItemsInfo.single { it.key == "model:lab/a" }.index) }
    }

    @Test fun backgroundFavoriteProjectionKeepsSnapshotIdentityUntilExplicitCatalogPublication() {
        val models = mutableStateOf(listOf(a, b))
        val favorites = mutableStateOf(FavoritesUiState(false, listOf(saved, missing)))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelListScreen(ModelListUiState(models = models.value, isLoading = false), favoritesState = favorites.value)
        } }
        compose.runOnIdle {
            favorites.value = FavoritesUiState(false, listOf(
                saved.copy(identity = saved.identity.copy(name = "Renamed Beta"), model = null),
                missing.copy(identity = missing.identity.copy(name = "Recovered"), model = ModelSummary("old/gone", "Recovered")),
            ))
        }
        compose.onNodeWithTag("model:lab/b").assertExists()
        compose.onNodeWithTag("favorite-unavailable:lab/b").assertDoesNotExist()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("favorite-unavailable:old/gone"))
        compose.onNodeWithText("Historical").assertIsDisplayed()
        compose.onNodeWithText("Recovered").assertDoesNotExist()
        // 用户取消收藏仍即时生效，不必等待目录发布。
        compose.runOnIdle { favorites.value = favorites.value.copy(favorites = favorites.value.favorites.take(1)) }
        compose.onNodeWithTag("favorite-unavailable:old/gone").assertDoesNotExist()
        // 模拟主动刷新发布新目录，才将 b 变成下架历史记录。
        compose.runOnIdle { models.value = listOf(a) }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("favorite-unavailable:lab/b"))
        compose.onNodeWithText("Renamed Beta").assertIsDisplayed()
        compose.onNodeWithTag("model:lab/b").assertDoesNotExist()
    }

    @Test fun readFailureKeepsCatalogAndRetryCanRestoreFavorites() {
        val favorites = mutableStateOf(FavoritesUiState(isLoading = false, hasError = true))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(ModelListUiState(models = listOf(a), isLoading = false),
                    favoritesState = favorites.value, onRetryFavorites = { favorites.value = FavoritesUiState(false) })
            }
        }
        compose.onNodeWithText("无法读取收藏列表，请重试。").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("无法读取收藏列表，请重试。").assertDoesNotExist()
        compose.onNodeWithTag("model:lab/a").assertExists()
    }

    @Test fun detailReturnPinsSourceWhileFavoriteChangesThenReorders() {
        val favorites = mutableStateOf(FavoritesUiState(false))
        lateinit var transition: FavoriteRemovalTransition
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(detailContent = { _, back ->
                    Button(onClick = { favorites.value = FavoritesUiState(false, listOf(saved)); back() }) {
                        Text("收藏并返回")
                    }
                }) { padding, listState, open ->
                    transition = LocalFavoriteRemovalTransition.current!!
                    ModelListScreen(ModelListUiState(models = listOf(a, b), isLoading = false),
                        bottomOverlayPadding = padding, listState = listState, onModelClick = open,
                        favoritesState = favorites.value)
                }
            }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/b"))
        compose.onNodeWithTag("model:lab/b").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("收藏并返回").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals("lab/b", transition.pinnedId) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.runOnIdle { assertNull(transition.pinnedId) }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/b"))
        compose.onNodeWithTag("model-favorite:lab/b", useUnmergedTree = true).assertExists()
    }
}
