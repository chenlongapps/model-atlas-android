package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelCardSkeletonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightSkeletonShimmersWithoutChangingItsSurface() = assertShimmer(darkTheme = false)

    @Test fun darkSkeletonShimmersWithoutChangingItsSurface() = assertShimmer(darkTheme = true)

    @Test fun narrowLargeFontSkeletonShimmersWithinTheCard() = assertShimmer(darkTheme = true, fontScale = 2f)

    @Test fun lightControlsKeepTheirBoundsWhenLoadingCompletes() = assertControlsKeepTheirBounds(darkTheme = false)

    @Test fun darkLargeFontControlsKeepTheirBoundsWhenLoadingCompletes() =
        assertControlsKeepTheirBounds(darkTheme = true, fontScale = 2f)

    @Test fun restoredQueryKeepsItsControlsAndFilterPlaceholdersWhenLoadingCompletes() =
        assertControlsKeepTheirBounds(
            darkTheme = false,
            query = ModelListQuery(search = "已加载", weights = WeightFilter.OPEN),
        )

    private fun assertControlsKeepTheirBounds(
        darkTheme: Boolean,
        fontScale: Float = 1f,
        query: ModelListQuery = ModelListQuery(),
    ) {
        val state = mutableStateOf(ModelListUiState(query = query))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ModelListScreen(
                        state.value,
                        modifier = Modifier.width(320.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        onQueryChange = { state.value = state.value.copy(query = it) },
                    )
                }
            }
        }
        compose.waitForIdle()
        val controlTags = buildList {
            add("catalog-query")
            add("model-search")
            add("model-filter")
            if (query.filterCount > 0) {
                add("active-filter:weights")
                add("reset-filters")
            }
        }
        val before = controlTags.map { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        val firstCardTop = compose.onNodeWithTag("model-skeleton:0").getUnclippedBoundsInRoot().top
        compose.runOnIdle {
            state.value = state.value.copy(
                models = listOf(ModelSummary("lab/loaded", "已加载模型", openWeights = true)),
                isLoading = false,
            )
        }
        compose.waitForIdle()
        controlTags.forEachIndexed { index, tag ->
            val after = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertEquals("$tag must keep its horizontal bounds", before[index].left, after.left)
            assertEquals("$tag must keep its horizontal bounds", before[index].right, after.right)
            // 可点击控件的合并语义可能落在内部 Surface；占位语义则覆盖最小触摸区域。
            // 比较中心与整体查询区高度，避免把读屏边界差异误判为布局移动。
            assertEquals(
                "$tag must keep its center (allowing pixel rounding)",
                ((before[index].top + before[index].bottom) / 2).value,
                ((after.top + after.bottom) / 2).value,
                0.5f,
            )
            if (tag == "catalog-query" || tag == "model-search") {
                assertEquals("$tag must not move or resize", before[index], after)
            }
        }
        assertEquals(firstCardTop, compose.onNodeWithTag("model:lab/loaded").getUnclippedBoundsInRoot().top)
        compose.onNodeWithTag("model-search").performTextReplacement("已加载")
        compose.runOnIdle { assertEquals("已加载", state.value.query.search) }
        compose.onNodeWithTag("model-filter").assertHasClickAction().performClick()
        compose.onNodeWithTag("model-filter-sheet").assertIsDisplayed()
    }

    @Test fun controlPlaceholdersShareShimmerWithoutInputOrFilterActions() {
        var queryChanges = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(
                    ModelListUiState(),
                    onQueryChange = { queryChanges++ },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                )
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val searchBefore = compose.onNodeWithTag("model-search").captureToImage()
        val filterBefore = compose.onNodeWithTag("model-filter").captureToImage()
        compose.mainClock.advanceTimeBy(600)
        assertTrue(changedPixels(searchBefore, compose.onNodeWithTag("model-search").captureToImage()) > 100)
        assertTrue(changedPixels(filterBefore, compose.onNodeWithTag("model-filter").captureToImage()) > 100)
        compose.onNodeWithTag("model-search")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus))
            .performTouchInput { click() }
        compose.onNodeWithTag("model-filter")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .performTouchInput { click() }
        compose.onAllNodes(isFocused()).assertCountEquals(0)
        compose.onNodeWithTag("model-filter-sheet").assertDoesNotExist()
        compose.onNodeWithText("筛选 · 0").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, queryChanges) }
    }

    private fun assertShimmer(darkTheme: Boolean, fontScale: Float = 1f) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                    Box(Modifier.width(320.dp).padding(16.dp)) {
                        ModelCardSkeleton(rememberModelSkeletonShimmer(true), index = 0)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val before = skeletonImage()
        compose.mainClock.advanceTimeBy(600)
        val after = skeletonImage()
        assertTrue("The highlight must move over placeholder shapes", changedPixels(before, after) > 100)
        val beforePixels = before.toPixelMap()
        val afterPixels = after.toPixelMap()
        val paddingY = with(compose.density) { 8.dp.roundToPx() }
        assertEquals("Shimmer must not wash over the card surface", beforePixels[before.width / 2, paddingY], afterPixels[after.width / 2, paddingY])
        val bounds = compose.onNodeWithTag("model-skeleton:0").getUnclippedBoundsInRoot()
        assertEquals(288f, (bounds.right - bounds.left).value, 0.5f)
        compose.onNodeWithContentDescription("正在加载模型目录…").assertIsDisplayed()
    }

    @Test fun loadingHasOneAccessibleProgressNodeAndBecomesRealModels() {
        val state = mutableStateOf(ModelListUiState())
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, windowInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )).assertCountEquals(1)
        compose.onNodeWithTag("model-skeleton:0").assertIsDisplayed()
        compose.onNodeWithText("模型目录 · 0 个模型").assertDoesNotExist()
        compose.onNodeWithText("发现模型，从这里开始").assertDoesNotExist()
        compose.runOnIdle {
            state.value = ModelListUiState(
                models = listOf(ModelSummary("lab/loaded", "已加载模型")),
                isLoading = false,
            )
        }
        compose.onNodeWithTag("model-skeleton:0").assertDoesNotExist()
        compose.onNodeWithContentDescription("正在加载模型目录…").assertDoesNotExist()
        compose.onNodeWithText("已加载模型").assertIsDisplayed()
        compose.onNodeWithText("模型目录 · 1 个模型").assertIsDisplayed()
    }

    @Test fun initialFailureRemovesSkeletonAndRetryRestoresIt() {
        val state = mutableStateOf(ModelListUiState())
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onRetry = { state.value = ModelListUiState() })
            }
        }
        compose.onNodeWithTag("model-skeleton:0").assertIsDisplayed()
        compose.onNodeWithTag("model-search").assertIsDisplayed()
        compose.onNodeWithTag("model-filter").assertIsDisplayed()
        compose.runOnIdle { state.value = ModelListUiState(isLoading = false, error = ModelListError.NETWORK) }
        compose.onNodeWithTag("model-skeleton:0").assertDoesNotExist()
        compose.onNodeWithTag("catalog-query").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("model-skeleton:0").assertIsDisplayed()
        compose.onNodeWithTag("model-search").assertIsDisplayed()
        compose.onNodeWithTag("model-filter").assertIsDisplayed()
    }

    @Test fun refreshingCachedModelsDoesNotShowSkeletons() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(ModelListUiState(
                    models = listOf(ModelSummary("lab/cached", "本地模型")),
                    isLoading = false,
                    isRefreshing = true,
                ))
            }
        }
        compose.onNodeWithTag("model-skeleton:0").assertDoesNotExist()
        compose.onNodeWithText("本地模型").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在加载模型目录…").assertDoesNotExist()
    }

    @Test fun hiddenPageStopsShimmerAndVisiblePageRestartsIt() {
        val visible = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalModelListVisible provides visible.value) {
                ModelAtlasTheme(dynamicColor = false) {
                    ModelCardSkeleton(rememberModelSkeletonShimmer(true), index = 0)
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        val hidden = skeletonImage()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0, changedPixels(hidden, skeletonImage()))
        compose.runOnIdle { visible.value = true }
        compose.mainClock.advanceTimeBy(700)
        assertTrue(changedPixels(hidden, skeletonImage()) > 100)
    }

    private fun skeletonImage() = compose.onNodeWithTag("model-skeleton:0").captureToImage()
}

class ModelCardSkeletonReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun disabledAnimationsKeepAStaticAccessibleSkeleton() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelCardSkeleton(rememberModelSkeletonShimmer(true), index = 0)
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val before = compose.onNodeWithTag("model-skeleton:0").captureToImage()
        compose.mainClock.advanceTimeBy(700)
        val after = compose.onNodeWithTag("model-skeleton:0").captureToImage()
        assertEquals(0, changedPixels(before, after))
        compose.onNodeWithContentDescription("正在加载模型目录…").assertIsDisplayed()
    }

    @Test fun disabledAnimationsKeepControlPlaceholdersStatic() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(ModelListUiState(), windowInsets = WindowInsets(0, 0, 0, 0))
            }
        }
        compose.mainClock.advanceTimeBy(100)
        val search = compose.onNodeWithTag("model-search").captureToImage()
        val filter = compose.onNodeWithTag("model-filter").captureToImage()
        compose.mainClock.advanceTimeBy(700)
        assertEquals(0, changedPixels(search, compose.onNodeWithTag("model-search").captureToImage()))
        assertEquals(0, changedPixels(filter, compose.onNodeWithTag("model-filter").captureToImage()))
    }
}

private fun changedPixels(before: ImageBitmap, after: ImageBitmap): Int {
    assertEquals(before.width, after.width)
    assertEquals(before.height, after.height)
    val first = before.toPixelMap()
    val second = after.toPixelMap()
    var changed = 0
    for (y in 0 until before.height) {
        for (x in 0 until before.width) {
            if (first[x, y] != second[x, y]) changed++
        }
    }
    return changed
}
