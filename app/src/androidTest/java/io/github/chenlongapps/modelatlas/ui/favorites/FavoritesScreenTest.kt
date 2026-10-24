package io.github.chenlongapps.modelatlas.ui.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.CatalogUpdateCheckResult
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailRoute
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailViewModel
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FavoritesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val model = ModelSummary("lab/a", "Test model", price = ModelPriceSummary("lab", "Lab", true,
        input = 0.0, tiers = listOf(ModelPriceTierSummary("context", 272_000, input = 1.0))))
    private val repository = FakeFavorites()
    private val detail = ModelDetailViewModel(FakeModels()).also { store.put("model", it) }
    private val favoriteDetail = FavoriteDetailViewModel(repository).also { store.put("favorite", it) }
    private val list = FavoritesViewModel(repository).also { store.put("list", it) }
    private lateinit var removal: FavoriteRemovalTransition
    @After fun close() = store.clear()

    @Test fun detailOnlyAddStaysThenRemoveReturnsToModelsSource() {
        show()
        compose.onNodeWithContentDescription("收藏模型").assertDoesNotExist()
        sourceModel("model-list").performClick()
        compose.onNodeWithContentDescription("收藏模型").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.runOnIdle { assertTrue(repository.items.value.isEmpty()) }
    }

    @Test fun sameModelInBothPagesReturnsToFavoritesBeforeRemovingSource() {
        repository.items.value = listOf(favorite())
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        sourceModel("favorites-list").performClick()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithTag("nav-favorites").assertIsSelected()
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").performClick()
        sourceModel("model-list").assertIsDisplayed()
    }

    @Test fun persistedRemovalKeepsTheSharedTargetUntilReturnThenFadesAndReflows() {
        val second = FavoriteModel(FavoriteIdentity("lab/b", "Second model", "lab"), 0,
            ModelSummary("lab/b", "Second model"))
        repository.items.value = listOf(favorite(), second)
        show(dark = true)
        compose.onNodeWithTag("nav-favorites").performClick()
        val source = sourceModel("favorites-list").fetchSemanticsNode().boundsInRoot
        val secondBefore = compose.onNodeWithTag("model:lab/b").fetchSemanticsNode().boundsInRoot.top
        val before = compose.onRoot().captureToImage().toPixelMap()
        sourceModel("favorites-list").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertEquals(listOf(second), repository.items.value)
            assertTrue(removal.awaitingReturn)
            assertEquals("lab/a", removal.retained?.favorite?.identity?.id)
        }
        val returning = changedBounds(before, compose.onRoot().captureToImage().toPixelMap())
        assertTrue("必须缩回原卡，不能直接整页淡出: $returning", returning.width > source.width + 1 && returning.width < before.width - 1)
        assertTrue(returning.height > source.height + 1 && returning.height < before.height - 1)
        assertEquals("返回途中后续卡片不能提前补位", secondBefore,
            compose.onNodeWithTag("model:lab/b", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("尚无收藏").assertDoesNotExist()

        compose.mainClock.advanceTimeUntil(1_500) { !removal.awaitingReturn }
        compose.waitForIdle()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.runOnIdle { assertNotNull("返回结束后还有独立的卡片淡出", removal.retained) }
        val fadeStart = compose.onRoot().captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(80)
        val fadeMiddle = compose.onRoot().captureToImage().toPixelMap()
        val x = source.left.toInt() + 8
        val y = source.center.y.toInt()
        assertNotEquals("卡片应在返回后淡出", fadeStart[x, y].toArgb(), fadeMiddle[x, y].toArgb())
        assertEquals("淡出完成前保持原位置", secondBefore,
            compose.onNodeWithTag("model:lab/b").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.advanceTimeUntil(500) { removal.retained == null }
        compose.mainClock.advanceTimeBy(80)
        val movingTop = compose.onNodeWithTag("model:lab/b").fetchSemanticsNode().boundsInRoot.top
        assertTrue("其余卡片应平滑补位", movingTop > source.top + 1 && movingTop < secondBefore - 1)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(source.top, compose.onNodeWithTag("model:lab/b").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun lastUnavailableFavoriteWaitsForFallbackReturnAndCardFadeBeforeEmptyState() {
        repository.items.value = listOf(favorite(available = false))
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        compose.onNodeWithTag("favorite-unavailable:lab/a").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue(repository.items.value.isEmpty())
            assertTrue(removal.awaitingReturn)
            assertNotNull(removal.retained)
        }
        compose.onNodeWithText("尚无收藏").assertDoesNotExist()
        compose.mainClock.advanceTimeUntil(1_500) { !removal.awaitingReturn }
        compose.waitForIdle()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithText("尚无收藏").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertNotNull(removal.retained) }
        compose.onNodeWithText("尚无收藏").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun externalRemovalDoesNotRetainSourceOrFreezeTheList() {
        repository.items.value = listOf(favorite())
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        sourceModel("favorites-list").performClick()
        compose.runOnIdle { repository.items.value = emptyList() }
        compose.runOnIdle { assertNull(removal.retained) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
    }

    @Test fun scrolledExpandedSourceKeepsItsExactBoundsDuringRemovalReturn() {
        fun other(index: Int) = FavoriteModel(FavoriteIdentity("lab/$index", "Other $index", "lab"), index.toLong(),
            ModelSummary("lab/$index", "Other $index"))
        repository.items.value = List(8, ::other) + favorite() + List(8) { other(it + 8) }
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        compose.onNodeWithTag("favorites-list").performScrollToNode(hasTestTag("model:lab/a"))
        compose.onNode(hasContentDescription("展开档位价格") and hasAnyAncestor(hasTestTag("favorites-list"))).performClick()
        val source = sourceModel("favorites-list").fetchSemanticsNode().boundsInRoot
        val neighbor = compose.onNodeWithTag("model:lab/8").fetchSemanticsNode().boundsInRoot
        sourceModel("favorites-list").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.mainClock.advanceTimeBy(160)
        assertEquals("未参与共享变换的相邻卡片保持位置，不滚回列表头部", neighbor,
            compose.onNodeWithTag("model:lab/8", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle {
            assertTrue(removal.awaitingReturn)
            assertEquals(model, removal.retained?.favorite?.model)
        }
        compose.mainClock.advanceTimeUntil(1_500) { !removal.awaitingReturn }
        compose.waitForIdle()
        // 共享元素的中间帧本来就会变换根坐标；交接后才核对原卡与已展开报价的精确尺寸。
        val retainedBounds = compose.onNode(hasTestTag("model:lab/a") and hasAnyAncestor(hasTestTag("favorites-list")),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(source, retainedBounds)
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("nav-favorites").assertIsSelected()
        sourceModel("favorites-list").assertDoesNotExist()
    }

    @Test fun lateListEmissionStillPlaysCardExitOnlyAfterReturn() {
        repository.items.value = listOf(favorite())
        repository.deferRemovalEmission = true
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        sourceModel("favorites-list").performClick()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.runOnIdle {
            assertFalse(removal.awaitingReturn)
            assertNotNull(removal.retained)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { repository.items.value = emptyList() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertNotNull("晚到的 Flow 也不能直接移除卡片", removal.retained) }
        compose.onNodeWithText("尚无收藏").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun normalBackKeepsFavoritePriceExpansionAndBothSourcesUseTheCorrectCard() {
        repository.items.value = listOf(favorite())
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        compose.onNode(hasContentDescription("展开档位价格") and hasAnyAncestor(hasTestTag("favorites-list"))).performClick()
        sourceModel("favorites-list").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-favorites").assertIsSelected()
        compose.onNode(hasContentDescription("收起档位价格") and hasAnyAncestor(hasTestTag("favorites-list"))).assertIsDisplayed()
        compose.onNodeWithTag("nav-models").performClick()
        sourceModel("model-list").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        sourceModel("model-list").assertIsDisplayed()
    }

    @Test fun unavailableFavoriteShowsOnlyHistoricalIdentityAndCanRemoveThroughDetail() {
        repository.items.value = listOf(favorite(available = false))
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        compose.onNodeWithTag("favorite-unavailable:lab/a").performClick()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithText("当前目录不可用").assertIsDisplayed()
        compose.onNodeWithText("最后一次可用时的身份信息").assertIsDisplayed()
        compose.onNodeWithText("Historical model").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-tabs").assertDoesNotExist()
        compose.onNodeWithTag("model-price-section").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("nav-favorites").assertIsSelected()
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
    }

    @Test fun writeFailureDoesNotNavigateAndRetryCanRemove() {
        repository.items.value = listOf(favorite())
        show()
        compose.onNodeWithTag("nav-favorites").performClick()
        sourceModel("favorites-list").performClick()
        compose.runOnIdle { repository.failWrite = true }
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithText("收藏操作未能保存，请重试。").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏").assertIsEnabled()
        compose.runOnIdle { assertNull(removal.retained) }
        compose.runOnIdle { repository.failWrite = false }
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("nav-favorites").assertIsSelected()
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
    }

    @Test fun priceExpansionAndFavoriteScrollSurviveSwitchAndAsyncRestoration() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(FavoritesUiState(false, listOf(favorite()) + List(20) { index ->
            FavoriteModel(FavoriteIdentity("lab/$index", "Other $index", "lab"), index.toLong(),
                ModelSummary("lab/$index", "Other $index"))
        }))
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(favoritesContent = { padding, click ->
                    FavoritesScreen(state.value, click, bottomOverlayPadding = padding)
                }) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-favorites").performClick()
        compose.onNodeWithContentDescription("展开档位价格").performClick()
        compose.onNodeWithTag("favorites-list").performScrollToNode(hasText("Other 12"))
        val before = compose.onNodeWithText("Other 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-favorites").performClick()
        assertEquals(before, compose.onNodeWithText("Other 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        val loaded = state.value
        compose.runOnIdle { state.value = FavoritesUiState() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = loaded }
        assertEquals(before, compose.onNodeWithText("Other 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("favorites-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
    }

    @Test fun arbitraryModelIdDoesNotCollideWithHeaderAndReadFailureClearsContentThenRetriesLocally() {
        val state = mutableStateOf(FavoritesUiState(false, listOf(FavoriteModel(
            FavoriteIdentity("header", "Header ID model", null), 1, ModelSummary("header", "Header ID model")))))
        var retries = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                FavoritesScreen(state.value, {}, onRetry = {
                    retries++
                    state.value = FavoritesUiState(isLoading = false)
                })
            }
        }
        compose.onNodeWithTag("model:header").assertIsDisplayed()
        compose.runOnIdle { state.value = FavoritesUiState(isLoading = false, hasError = true) }
        compose.onNodeWithTag("model:header").assertDoesNotExist()
        compose.onNodeWithText("无法读取收藏列表，请重试。").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
        compose.onNodeWithContentDescription("搜索模型").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun fiveTabsAndHistoricalIdentityFitNarrowLargeFontDarkRtl() {
        val state = FavoritesUiState(false, listOf(favorite(false)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ModelAtlasScaffold(favoritesContent = { padding, click ->
                            FavoritesScreen(state, click, bottomOverlayPadding = padding)
                        }) { _, _, _ -> }
                    }
                }
            }
        }
        compose.onNodeWithTag("nav-favorites").performClick().assertIsSelected()
        for ((tag, label) in listOf("nav-models" to "模型", "nav-favorites" to "收藏", "nav-compare" to "对比", "nav-settings" to "设置", "nav-debug" to "调试")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            assertFalse(layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        compose.onNodeWithTag("favorites-list").performScrollToNode(hasText("当前目录不可用"))
        compose.onNodeWithText("当前目录不可用").assertIsDisplayed()
    }

    private fun sourceModel(list: String) = compose.onNode(hasTestTag("model:lab/a") and hasAnyAncestor(hasTestTag(list)))

    private fun show(dark: Boolean = false) {
        compose.setContent {
            ModelAtlasTheme(darkTheme = dark, dynamicColor = false) {
                ModelAtlasScaffold(
                    favoritesContent = { padding, click ->
                        removal = LocalFavoriteRemovalTransition.current!!
                        FavoritesRoute(padding, click, list)
                    },
                    detailContent = { id, back -> ModelDetailRoute(id, back, detail, favoriteDetail) },
                ) { padding, state, click ->
                    ModelListScreen(ModelListUiState(models = listOf(model), isLoading = false),
                        bottomOverlayPadding = padding, listState = state, onModelClick = click)
                }
            }
        }
    }

    private fun changedBounds(before: PixelMap, after: PixelMap): Rect {
        var left = before.width
        var top = before.height
        var right = -1
        var bottom = -1
        for (y in 0 until before.height) for (x in 0 until before.width) {
            if (before[x, y].toArgb() != after[x, y].toArgb()) {
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        assertTrue("中间帧应该仍有转场画面", right >= left && bottom >= top)
        return Rect(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
    }

    private fun favorite(available: Boolean = true) = FavoriteModel(FavoriteIdentity("lab/a", "Historical model", "lab"), 1,
        model.takeIf { available })

    private inner class FakeFavorites : FavoriteRepository {
        val items = MutableStateFlow<List<FavoriteModel>>(emptyList())
        var failWrite = false
        var deferRemovalEmission = false
        override fun observeFavorites() = items
        override fun observeFavorite(id: String) = items.map { all -> all.find { it.identity.id == id }?.identity }
        override suspend fun add(id: String) { if (failWrite) error("write"); items.value = listOf(favorite()) }
        override suspend fun remove(id: String) {
            if (failWrite) error("write")
            if (!deferRemovalEmission) items.value = items.value.filterNot { it.identity.id == id }
        }
    }

    private inner class FakeModels : ModelRepository {
        override fun observeModelDetail(id: String) = flowOf(
            if (repository.items.value.any { it.model == null }) null else ModelDetail(id, model.name, fetchedAtEpochMillis = 0))
        override fun observeModels() = flowOf(listOf(model))
        override suspend fun hasLocalCatalog() = true
        override suspend fun checkForUpdates(): CatalogUpdateCheckResult = error("No network")
        override suspend fun refresh(): Boolean = error("No network")
        override suspend fun applyPendingUpdate(): Boolean = error("No network")
    }
}

class FavoriteRemovalAnimationDisabledTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    })

    @Test fun zeroAnimationScaleReleasesSourceAndShowsEmptyWithoutWaiting() {
        val favorite = FavoriteModel(FavoriteIdentity("lab/a", "Model", "lab"), 1, ModelSummary("lab/a", "Model"))
        val state = mutableStateOf(FavoritesUiState(false, listOf(favorite)))
        val removal = FavoriteRemovalTransition()
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalFavoriteRemovalTransition provides removal) {
                    FavoritesScreen(state.value, {})
                }
            }
        }
        compose.runOnIdle {
            removal.begin("lab/a")
            state.value = FavoritesUiState(false)
        }
        compose.runOnIdle { assertNotNull(removal.retained) }
        compose.onNodeWithText("尚无收藏").assertDoesNotExist()
        compose.runOnIdle { removal.finishReturn() }
        compose.onNodeWithText("尚无收藏").assertIsDisplayed()
        compose.runOnIdle { assertNull(removal.retained) }
    }
}
