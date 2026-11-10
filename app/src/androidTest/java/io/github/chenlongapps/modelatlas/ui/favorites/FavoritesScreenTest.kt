package io.github.chenlongapps.modelatlas.ui.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
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
    private val catalog = mutableStateOf(listOf(model))
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

    @Test fun cancellingLastAvailableFavoriteKeepsOneOrdinaryCatalogCard() {
        repository.items.value = listOf(favorite())
        show()
        compose.onNodeWithTag("nav-favorites").assertDoesNotExist()
        compose.onAllNodesWithTag("model:lab/a").assertCountEquals(1)
        sourceModel("model-list").performClick()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("model-favorite:lab/a", useUnmergedTree = true).assertDoesNotExist()
        compose.onAllNodesWithTag("model:lab/a").assertCountEquals(1)
        sourceModel("model-list").assertIsDisplayed()
    }

    @Test fun persistedRemovalKeepsAvailableSourceUntilReturnThenReordersWithoutDeletingIt() {
        val second = FavoriteModel(FavoriteIdentity("lab/b", "Second model", "lab"), 0,
            ModelSummary("lab/b", "Second model"))
        repository.items.value = listOf(favorite())
        catalog.value = listOf(second.model!!, model)
        show(dark = true)
        val secondBefore = compose.onNodeWithTag("model:lab/b").fetchSemanticsNode().boundsInRoot.top
        // 目录发布时间顺序为 b、a，收藏只有 a，使其先置顶；取消后才允许降回 b 后面。
        val pinnedSource = sourceModel("model-list").fetchSemanticsNode().boundsInRoot
        val before = compose.onRoot().captureToImage().toPixelMap()
        sourceModel("model-list").performClick()
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue(repository.items.value.isEmpty())
            assertTrue(removal.awaitingReturn)
            assertEquals("lab/a", removal.retained?.favorite?.identity?.id)
        }
        val returning = changedBounds(before, compose.onRoot().captureToImage().toPixelMap())
        assertTrue("必须缩回原卡，不能直接整页淡出: $returning", returning.width > pinnedSource.width + 1 && returning.width < before.width - 1)
        assertTrue(returning.height > pinnedSource.height + 1 && returning.height < before.height - 1)
        assertEquals("返回途中后续卡片不能提前补位", secondBefore,
            compose.onNodeWithTag("model:lab/b", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.advanceTimeUntil(1_500) { !removal.awaitingReturn }
        compose.waitForIdle()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = true
        sourceModel("model-list").assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("model:lab/b").fetchSemanticsNode().boundsInRoot.top <
            sourceModel("model-list").fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle { assertNull(removal.retained) }
    }

    @Test fun lastUnavailableFavoriteWaitsForFallbackReturnAndCardFadeBeforeEmptyState() {
        repository.items.value = listOf(favorite(available = false))
        catalog.value = emptyList()
        show()
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
        compose.onNodeWithText("目录暂时为空").assertDoesNotExist()
        compose.mainClock.advanceTimeUntil(1_500) { !removal.awaitingReturn }
        compose.waitForIdle()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithText("目录暂时为空").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertNotNull(removal.retained) }
        compose.onNodeWithText("目录暂时为空").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("目录暂时为空").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun externalRemovalDoesNotRetainSourceOrFreezeTheList() {
        repository.items.value = listOf(favorite())
        show()
        sourceModel("model-list").performClick()
        compose.runOnIdle { repository.items.value = emptyList() }
        compose.runOnIdle { assertNull(removal.retained) }
        compose.onNodeWithContentDescription("返回").performClick()
        sourceModel("model-list").assertIsDisplayed()
        compose.onNodeWithTag("model-favorite:lab/a", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun scrolledExpandedSourceKeepsItsExactBoundsDuringRemovalReturn() {
        fun other(index: Int) = FavoriteModel(FavoriteIdentity("lab/$index", "Other $index", "lab"), index.toLong(),
            ModelSummary("lab/$index", "Other $index"))
        repository.items.value = List(8, ::other) + favorite() + List(8) { other(it + 8) }
        catalog.value = repository.items.value.mapNotNull { it.model }
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/a"))
        compose.onNode(hasContentDescription("展开档位价格") and hasAnyAncestor(hasTestTag("model-list"))).performClick()
        val source = sourceModel("model-list").fetchSemanticsNode().boundsInRoot
        val neighbor = compose.onNodeWithTag("model:lab/8").fetchSemanticsNode().boundsInRoot
        sourceModel("model-list").performClick()
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
        val retainedBounds = compose.onNode(hasTestTag("model:lab/a") and hasAnyAncestor(hasTestTag("model-list")),
            useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(source, retainedBounds)
        compose.mainClock.advanceTimeBy(1_000)
        compose.mainClock.autoAdvance = true
        // 来源滚动已隐藏底栏，详情返回应保留该状态；先反向滚动再使用导航。
        compose.onNodeWithTag("model-list").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, 100f), 400) }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/a"))
        sourceModel("model-list").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
    }

    @Test fun lateListEmissionStillPlaysCardExitOnlyAfterReturn() {
        repository.items.value = listOf(favorite(false))
        catalog.value = emptyList()
        repository.deferRemovalEmission = true
        show()
        compose.onNodeWithTag("favorite-unavailable:lab/a").performClick()
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
        compose.onNodeWithText("目录暂时为空").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("目录暂时为空").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test fun buttonAndSystemBackKeepFavoritePriceExpansionAndCatalogSource() {
        repository.items.value = listOf(favorite())
        show()
        compose.onNodeWithContentDescription("展开档位价格").performClick()
        sourceModel("model-list").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
        sourceModel("model-list").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        sourceModel("model-list").assertIsDisplayed()
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf(favorite()), repository.items.value) }
    }

    @Test fun unavailableFavoriteShowsOnlyHistoricalIdentityAndCanRemoveThroughDetail() {
        repository.items.value = listOf(favorite(available = false))
        catalog.value = emptyList()
        show()
        compose.onNode(hasText("lab/a") and hasAnyAncestor(hasTestTag("model-list")), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("favorite-unavailable:lab/a").performClick()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithText("当前目录不可用").assertIsDisplayed()
        compose.onNodeWithText("最后一次可用时的身份信息").assertIsDisplayed()
        compose.onNodeWithText("Historical model").assertIsDisplayed()
        compose.onNode(hasText("lab/a") and hasAnyAncestor(hasTestTag("model-detail-screen")), useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithTag("model-detail-tabs").assertDoesNotExist()
        compose.onNodeWithTag("model-price-section").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithText("目录暂时为空").assertIsDisplayed()
    }

    @Test fun writeFailureDoesNotNavigateAndRetryCanRemove() {
        repository.items.value = listOf(favorite())
        show()
        sourceModel("model-list").performClick()
        compose.runOnIdle { repository.failWrite = true }
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithText("收藏操作未能保存，请重试。").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("取消收藏").assertIsEnabled()
        compose.runOnIdle { assertNull(removal.retained) }
        compose.runOnIdle { repository.failWrite = false }
        compose.onNodeWithContentDescription("取消收藏").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        sourceModel("model-list").assertIsDisplayed()
        compose.onNodeWithTag("model-favorite:lab/a", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun priceExpansionAndFavoriteScrollSurviveSwitchAndAsyncRestoration() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(FavoritesUiState(false, listOf(favorite()) + List(20) { index ->
            FavoriteModel(FavoriteIdentity("lab/$index", "Other $index", "lab"), index.toLong(),
                ModelSummary("lab/$index", "Other $index"))
        }))
        val models = state.value.favorites.mapNotNull { it.model }
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(touchExplorationEnabled = true) { padding, listState, click ->
                    ModelListScreen(ModelListUiState(models = models, isLoading = false),
                        favoritesState = state.value, onModelClick = click,
                        bottomOverlayPadding = padding, listState = listState)
                }
            }
        }
        compose.onNodeWithContentDescription("展开档位价格").performClick()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("Other 12"))
        val before = compose.onNodeWithText("Other 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("Other 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        val loaded = state.value
        compose.runOnIdle { state.value = FavoritesUiState() }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = loaded }
        assertEquals(before, compose.onNodeWithText("Other 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("model-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
    }

    @Test fun arbitraryModelIdDoesNotCollideWithHeaderAndFavoriteReadFailureKeepsCatalogThenRetriesLocally() {
        val state = mutableStateOf(FavoritesUiState(false, listOf(FavoriteModel(
            FavoriteIdentity("header", "Header ID model", null), 1, ModelSummary("header", "Header ID model")))))
        var retries = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(ModelListUiState(models = listOf(ModelSummary("header", "Header ID model")), isLoading = false),
                    favoritesState = state.value, onRetryFavorites = {
                    retries++
                    state.value = FavoritesUiState(isLoading = false)
                })
            }
        }
        compose.onNodeWithTag("model:header").assertIsDisplayed()
        compose.runOnIdle { state.value = FavoritesUiState(isLoading = false, hasError = true) }
        compose.onNodeWithTag("model:header").assertIsDisplayed()
        compose.onNodeWithText("无法读取收藏列表，请重试。").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("model:header").assertIsDisplayed()
        compose.onNodeWithText("无法读取收藏列表，请重试。").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索模型").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun fiveTabsAndHistoricalIdentityFitNarrowLargeFontDarkRtl() {
        val state = FavoritesUiState(false, listOf(favorite(false)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ModelAtlasScaffold(touchExplorationEnabled = true) { padding, listState, click ->
                            ModelListScreen(ModelListUiState(models = emptyList(), isLoading = false),
                                favoritesState = state, onModelClick = click,
                                bottomOverlayPadding = padding, listState = listState)
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-favorites").assertDoesNotExist()
        for ((tag, label) in listOf("nav-models" to "模型", "nav-benchmarks" to "评测", "nav-compare" to "对比", "nav-settings" to "设置", "nav-debug" to "调试")) {
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
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("当前目录不可用"))
        compose.onNodeWithText("当前目录不可用").assertIsDisplayed()
    }

    private fun sourceModel(list: String) = compose.onNode(hasTestTag("model:lab/a") and hasAnyAncestor(hasTestTag(list)))

    private fun show(dark: Boolean = false) {
        compose.setContent {
            ModelAtlasTheme(darkTheme = dark, dynamicColor = false) {
                ModelAtlasScaffold(
                    detailContent = { id, back -> ModelDetailRoute(id, back, detail, favoriteDetail) },
                ) { padding, state, click ->
                    removal = LocalFavoriteRemovalTransition.current!!
                    val favorites by list.uiState.collectAsStateWithLifecycle()
                    ModelListScreen(ModelListUiState(models = catalog.value, isLoading = false),
                        bottomOverlayPadding = padding, listState = state, onModelClick = click,
                        favoritesState = favorites, onRetryFavorites = list::retry)
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
            catalog.value.find { it.id == id }?.let { ModelDetail(id, it.name, fetchedAtEpochMillis = 0) })
        override fun observeModels() = flowOf(listOf(model))
        override suspend fun hasLocalCatalog() = true
        override suspend fun refresh(): Boolean = error("No network")
    }
}

class FavoriteRemovalAnimationDisabledTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor: Float = 0f
    })

    @Test fun zeroAnimationScaleReleasesUnavailableSourceAndShowsEmptyWithoutWaiting() {
        val favorite = FavoriteModel(FavoriteIdentity("lab/a", "Model", "lab"), 1, null)
        val state = mutableStateOf(FavoritesUiState(false, listOf(favorite)))
        val removal = FavoriteRemovalTransition()
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalFavoriteRemovalTransition provides removal) {
                    ModelListScreen(ModelListUiState(models = emptyList(), isLoading = false), favoritesState = state.value)
                }
            }
        }
        compose.runOnIdle {
            removal.begin("lab/a")
            state.value = FavoritesUiState(false)
        }
        compose.runOnIdle { assertNotNull(removal.retained) }
        compose.onNodeWithText("目录暂时为空").assertDoesNotExist()
        compose.runOnIdle { removal.finishReturn() }
        compose.onNodeWithText("目录暂时为空").assertIsDisplayed()
        compose.runOnIdle { assertNull(removal.retained) }
    }
}
