package io.github.chenlongapps.modelatlas.ui.debug

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DebugFeatureTest {
    @get:Rule val compose = createComposeRule()
    private val info = DebugDeviceInfo(
        "1.0-debug", 1, "io.github.chenlongapps.modelatlas.debug", true, "Google", "Pixel Test", "17", 37,
    )
    private val state = DebugUiState(statistics = DebugCatalogStatistics(1200, 60, 3200))

    @Test fun debugIsTopLevelAndTabSwitchesPreserveCatalogAndDebugScrollPositions() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(
                    debugContent = { padding, _, onOpen -> DebugScreen(state, info, {}, {}, onOpenDatabase = onOpen, bottomOverlayPadding = padding) },
                ) { padding, list, _ ->
                    ModelListScreen(
                        ModelListUiState(models = List(20) { ModelSummary("lab/$it", "测试模型 $it") }, isLoading = false),
                        bottomOverlayPadding = padding, listState = list,
                    )
                }
            }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-debug").performClick().assertIsSelected()
        compose.onNodeWithTag("debug-screen").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        assertIndicatorMatches("nav-debug")
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("手动刷新目录"))
        val debugTop = compose.onNodeWithText("手动刷新目录").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("debug-screen").assertIsNotDisplayed()
        compose.onNodeWithTag("debug-entry").assertDoesNotExist()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("nav-debug").performClick()
        assertEquals(debugTop, compose.onNodeWithText("手动刷新目录").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun debugDestinationAndScrollSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(debugContent = { padding, _, onOpen -> DebugScreen(state, info, {}, {}, onOpenDatabase = onOpen, bottomOverlayPadding = padding) }) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-debug").performClick()
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("手动刷新目录"))
        val before = compose.onNodeWithText("手动刷新目录").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("nav-debug").assertIsSelected()
        compose.onNodeWithTag("debug-screen").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("手动刷新目录").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
    }

    @Test fun fourTabsSupportSwipesBoundaryPagesAndNavigationDragging() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(debugContent = { padding, _, onOpen -> DebugScreen(state, info, {}, {}, onOpenDatabase = onOpen, bottomOverlayPadding = padding) }) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-debug").assertIsSelected()
        assertIndicatorMatches("nav-debug")
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-debug").assertIsSelected()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-compare").assertIsSelected()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        dragNavigation("nav-models", "nav-debug")
        compose.onNodeWithTag("nav-debug").assertIsSelected()
        assertIndicatorMatches("nav-debug")
        dragNavigation("nav-debug", "nav-settings")
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        assertIndicatorMatches("nav-settings")
    }

    @Test fun onlySettledVisibleDebugTabReceivesEntrySignal() {
        var entries = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(debugContent = { _, visible, _ ->
                    LaunchedEffect(visible) { if (visible) entries++ }
                }) { _, _, _ -> }
            }
        }
        compose.runOnIdle { assertEquals(0, entries) }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.runOnIdle { assertEquals(0, entries) }
        compose.onNodeWithTag("nav-debug").performClick()
        compose.runOnIdle { assertEquals(1, entries) }
        compose.onNodeWithTag("nav-debug").performClick()
        compose.runOnIdle { assertEquals(1, entries) }
        compose.onNodeWithTag("nav-models").performClick()
        compose.onNodeWithTag("nav-debug").performClick()
        compose.runOnIdle { assertEquals(2, entries) }
    }

    @Test fun settingsAndDebugDoubleClicksDoNotResetCatalog() = assertOtherTabsDoNotResetCatalog(LayoutDirection.Ltr)

    @Test fun rtlSettingsAndDebugDoubleClicksDoNotResetCatalog() = assertOtherTabsDoNotResetCatalog(LayoutDirection.Rtl)

    private fun assertOtherTabsDoNotResetCatalog(direction: LayoutDirection) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                ModelAtlasTheme(dynamicColor = false) {
                    ModelAtlasScaffold { padding, list, _ ->
                        ModelListScreen(
                            ModelListUiState(models = List(20) { ModelSummary("lab/$it", "测试模型 $it") }, isLoading = false),
                            bottomOverlayPadding = padding, listState = list,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        // 测试中间标签靠近模型的一侧：旧的二等分命中范围会把这里误识别为模型。
        compose.onNodeWithTag("nav-compare").performTouchInput {
            doubleClick(Offset(width * if (direction == LayoutDirection.Ltr) 0.15f else 0.85f, centerY))
        }
        compose.onNodeWithTag("nav-compare").assertIsSelected()
        compose.onNodeWithTag("nav-settings").performTouchInput {
            doubleClick(Offset(width * if (direction == LayoutDirection.Ltr) 0.15f else 0.85f, centerY))
        }
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("nav-debug").performTouchInput { doubleClick() }
        compose.onNodeWithTag("nav-debug").assertIsSelected()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun lastDiagnosticActionCanScrollFullyAboveFloatingNavigation() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(debugContent = { padding, _, onOpen -> DebugScreen(state, info, {}, {}, onOpenDatabase = onOpen, bottomOverlayPadding = padding) }) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-debug").performClick()
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("复制诊断信息"))
        compose.onNodeWithTag("debug-content").performTouchInput { swipeUp() }
        val action = compose.onNodeWithText("复制诊断信息").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(action.bottom <= navigation.top)
    }

    @Test fun readingAndRefreshingDisableBothOperationsAndShowProgress() {
        val uiState = mutableStateOf(state.copy(isRefreshing = true))
        var refreshes = 0
        var reads = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                DebugScreen(uiState.value, info, { reads++ }, { refreshes++ })
            }
        }
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("手动刷新目录"))
        compose.onNodeWithTag("debug-refresh").assertIsNotEnabled()
        compose.onNodeWithText("正在刷新目录…").assertIsDisplayed()
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("重读统计"))
        compose.onNodeWithText("重读统计").assertIsNotEnabled()
        compose.runOnIdle { uiState.value = state.copy(isReading = true) }
        compose.onNodeWithText("正在读取本地目录…").assertIsDisplayed()
        compose.onNodeWithText("重读统计").assertIsNotEnabled()
        compose.runOnIdle { uiState.value = state }
        compose.onNodeWithText("重读统计").assertIsEnabled().performClick()
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("手动刷新目录"))
        compose.onNodeWithTag("debug-refresh").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, reads); assertEquals(1, refreshes) }
    }

    @Test fun unreadStatisticsAreNotInventedAndFailuresRemainVisibleAlongsideOldCounts() {
        val uiState = mutableStateOf(DebugUiState(statisticsError = true))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) { DebugScreen(uiState.value, info, {}, {}) }
        }
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("本地目录"))
        compose.onNodeWithText("规范模型数").assertDoesNotExist()
        compose.onNodeWithText("本地统计读取失败", substring = true).assertIsDisplayed()
        compose.runOnIdle { uiState.value = state.copy(statisticsError = true, refreshResult = DebugRefreshResult.RATE_LIMITED) }
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("1200"))
        compose.onNodeWithText("1200").assertIsDisplayed()
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("请求受限", substring = true))
        compose.onNodeWithText("请求受限", substring = true).assertIsDisplayed()
    }

    @Test fun copiesOnlyDisplayedDiagnosticInformation() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) { DebugScreen(state, info, {}, {}) }
        }
        compose.onNodeWithTag("debug-content").performScrollToNode(hasText("复制诊断信息"))
        compose.onNodeWithText("复制诊断信息").performClick()
        compose.onNodeWithText("诊断信息已复制").assertIsDisplayed()
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip!!.getItemAt(0).text.toString()
            assertTrue(text.contains("Application ID: ${info.applicationId}"))
            assertTrue(text.contains("规范模型数: 1200"))
            assertTrue(text.contains("设备型号: Pixel Test"))
            assertFalse(text.contains("serial", ignoreCase = true))
        }
    }

    @Test fun narrowDarkRtlPageSupportsDoubleFontWithoutClipping() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) {
                        ModelAtlasScaffold(debugContent = { padding, _, onOpen -> DebugScreen(state, info, {}, {}, onOpenDatabase = onOpen, bottomOverlayPadding = padding) }) { _, _, _ -> }
                    }
                }
            }
        }
        compose.onNodeWithTag("nav-debug").performClick().assertIsSelected()
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        assertIndicatorMatches("nav-debug")
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        for (text in listOf(info.applicationId, "手动刷新目录", "复制诊断信息")) {
            compose.onNodeWithTag("debug-content").performScrollToNode(hasText(text))
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse(layout.isLineEllipsized(line))
            }
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
    }

    private fun assertIndicatorMatches(tag: String) {
        val indicator = compose.onNodeWithTag("nav-indicator").fetchSemanticsNode().boundsInRoot
        val item = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertEquals(item.center.x, indicator.center.x, 1f)
        assertEquals(item.width, indicator.width, 1.01f)
    }

    private fun dragNavigation(from: String, to: String) {
        val bar = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag(from).fetchSemanticsNode().boundsInRoot.center - bar.topLeft
        val end = compose.onNodeWithTag(to).fetchSemanticsNode().boundsInRoot.center - bar.topLeft
        compose.onNodeWithTag("glass-navigation").performTouchInput { swipe(start, end, 500) }
    }
}
