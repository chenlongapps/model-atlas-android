package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogStatusNoticeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightNoticeSupportsAllStatesAndTransientResults() {
        assertNoticeStates(darkTheme = false)
    }

    @Test fun darkNoticeSupportsAllStatesAndTransientResults() {
        assertNoticeStates(darkTheme = true)
    }

    private fun assertNoticeStates(darkTheme: Boolean) {
        val cached = ModelListUiState(
            models = listOf(ModelSummary("lab/cached", "本地模型")),
            isLoading = false,
        )
        val state = mutableStateOf(cached)
        var applies = 0
        var refreshes = 0
        var dismissals = 0
        compose.setContent {
            ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                ModelListScreen(
                    uiState = state.value,
                    onApplyUpdate = { applies++ },
                    onRefresh = { refreshes++ },
                    onDismissNotice = {
                        dismissals++
                        state.value = state.value.copy(message = null, refreshError = null)
                    },
                )
            }
        }
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        compose.runOnIdle { state.value = cached.copy(updateAvailable = true) }
        compose.onNodeWithText("发现新数据，可更新模型目录").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val actionableBounds = compose.onNodeWithTag("catalog-notice").getUnclippedBoundsInRoot()
        val actionableHeight = actionableBounds.bottom - actionableBounds.top
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithText("发现新数据，可更新模型目录").assertIsDisplayed()
        compose.onNodeWithText("立即更新").performClick()
        compose.onNodeWithText("关闭").assertDoesNotExist()
        assertEquals(1, applies)

        // 刷新期间只保留上方的下拉刷新指示器，内联提醒不占位。
        compose.runOnIdle { state.value = state.value.copy(isRefreshing = true) }
        assertOnlyPullToRefreshIndicatorIsVisible()
        compose.onNodeWithText("立即更新").assertDoesNotExist()
        compose.onNodeWithText("关闭").assertDoesNotExist()

        // 提交失败仍可直接重试暂存更新；关闭错误后恢复待更新提示。
        compose.runOnIdle { state.value = cached.copy(updateAvailable = true, refreshError = ModelListError.STORAGE) }
        compose.onNodeWithText("无法读写本地模型目录，请重试。").assertIsDisplayed()
        compose.onNodeWithText("立即更新").performClick()
        assertEquals(2, applies)
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("发现新数据，可更新模型目录").assertIsDisplayed()

        compose.runOnIdle { state.value = cached.copy(refreshError = ModelListError.NETWORK) }
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, refreshes)
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()

        for ((message, text) in listOf(
            ModelListMessage.UPDATED to "模型目录已更新",
            ModelListMessage.UP_TO_DATE to "已是最新数据",
        )) {
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { state.value = cached.copy(message = message) }
            compose.mainClock.advanceTimeBy(250)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.onNodeWithText("关闭").assertDoesNotExist()
            compose.onNodeWithText("重试").assertDoesNotExist()
            compose.onNodeWithText("立即更新").assertDoesNotExist()
            val bounds = compose.onNodeWithTag("catalog-notice").getUnclippedBoundsInRoot()
            assertTrue("Success must not retain the action area", bounds.bottom - bounds.top < actionableHeight)
            compose.mainClock.advanceTimeBy(2_500)
            compose.onNodeWithText(text).assertIsDisplayed()
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
            compose.mainClock.autoAdvance = true
        }
        assertEquals(4, dismissals)
        compose.onNodeWithText("本地模型").assertIsDisplayed()
    }

    @Test fun successRespectsTheAccessibilityRecommendedTimeout() {
        val state = mutableStateOf(ModelListUiState(isLoading = false))
        val accessibility = object : AccessibilityManager {
            override fun calculateRecommendedTimeoutMillis(
                originalTimeoutMillis: Long,
                containsIcons: Boolean,
                containsText: Boolean,
                containsControls: Boolean,
            ): Long {
                assertEquals(3_000L, originalTimeoutMillis)
                assertTrue(containsIcons)
                assertTrue(containsText)
                assertFalse(containsControls)
                return 12_000L
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides accessibility) {
                ModelAtlasTheme(dynamicColor = false) {
                    ModelListScreen(
                        uiState = state.value,
                        onDismissNotice = { state.value = state.value.copy(message = null, refreshError = null) },
                    )
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(message = ModelListMessage.UPDATED) }
        compose.mainClock.advanceTimeBy(9_000)
        compose.onNodeWithText("模型目录已更新").assertIsDisplayed()
        compose.onNodeWithText("关闭").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
    }

    @Test fun oldSuccessTimeoutDoesNotClearANewerErrorOrRefresh() {
        val state = mutableStateOf(ModelListUiState(isLoading = false))
        var dismissals = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(
                    uiState = state.value,
                    onDismissNotice = {
                        dismissals++
                        state.value = state.value.copy(message = null, refreshError = null)
                    },
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(message = ModelListMessage.UP_TO_DATE) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { state.value = state.value.copy(refreshError = ModelListError.NETWORK) }
        compose.mainClock.advanceTimeBy(60_000)
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        assertEquals(0, dismissals)

        compose.runOnIdle { state.value = state.value.copy(refreshError = null) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { state.value = state.value.copy(isRefreshing = true, message = null) }
        compose.mainClock.advanceTimeBy(60_000)
        assertOnlyPullToRefreshIndicatorIsVisible()
        assertEquals(0, dismissals)
    }

    private fun assertOnlyPullToRefreshIndicatorIsVisible() {
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        compose.onAllNodes(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo.Indeterminate,
        )).assertCountEquals(1)[0].assertIsDisplayed()
    }

    @Test fun successExpiresEvenWhenItsListItemIsNotComposed() {
        val state = mutableStateOf(ModelListUiState(
            isLoading = false,
            models = List(40) { ModelSummary("lab/model-$it", "模型 $it") },
        ))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(
                    uiState = state.value,
                    onDismissNotice = { state.value = state.value.copy(message = null, refreshError = null) },
                )
            }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/model-20"))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(message = ModelListMessage.UP_TO_DATE) }
        compose.mainClock.advanceTimeBy(4_000)
        compose.runOnIdle { assertEquals(null, state.value.message) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-header"))
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
    }

    @Test fun narrowScreenAndDoubleFontKeepMessageAndActionsUnclipped() {
        val message = "无法连接模型目录，请检查网络后重试。"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                ModelAtlasTheme(dynamicColor = false) {
                    ModelListScreen(
                        uiState = ModelListUiState(
                            isLoading = false,
                            updateAvailable = true,
                            refreshError = ModelListError.NETWORK,
                        ),
                        modifier = Modifier.width(320.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                    )
                }
            }
        }
        for (text in listOf(message, "关闭", "立即更新")) {
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            node.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$text height overflow", layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue("$text clips at start", layout.getLineLeft(line) >= -1f)
                assertTrue("$text clips at end", layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse("$text is ellipsized", layout.isLineEllipsized(line))
            }
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
        // 大字号可以增高按钮，但不能低于 Material 的最小触摸高度。
        val action = compose.onNodeWithText("立即更新")
        assertTrue(action.fetchSemanticsNode().boundsInRoot.height >= with(compose.density) { 48.dp.toPx() })
    }
}
