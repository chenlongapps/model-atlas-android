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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        compose.runOnIdle { state.value = ModelListUiState(isLoading = false, error = ModelListError.NETWORK) }
        compose.onNodeWithTag("model-skeleton:0").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("model-skeleton:0").assertIsDisplayed()
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
