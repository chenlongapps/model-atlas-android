package io.github.chenlongapps.modelatlas.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelAtlasAppTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tabsSwitchBetweenRealCatalogAndSettings() {
        show()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithText("测试模型 0").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        compose.onNodeWithText("数据来源与许可").assertIsDisplayed()
        compose.onNodeWithTag("model-list").assertIsNotDisplayed()
        compose.onNodeWithTag("nav-models").performClick().assertIsSelected()
        compose.onNodeWithText("测试模型 0").assertIsDisplayed()
    }

    @Test fun pageSwipeAndNavigationDragKeepSelectionAndIndicatorInSync() {
        show()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-compare").assertIsSelected()
        assertIndicatorMatches("nav-compare")
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        assertIndicatorMatches("nav-settings")
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-compare").assertIsSelected()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertIndicatorMatches("nav-models")
        dragNavigationToSettings(duration = 500)
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        assertIndicatorMatches("nav-settings")
    }

    @Test fun tabSwitchPreservesListScrollPosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performClick()
        val after = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
    }

    @Test fun doubleClickModelsScrollsToExactTopWithoutRefreshing() {
        var refreshes = 0
        show(onRefresh = { refreshes++ })
        val initialTop = compose.onNodeWithTag("catalog-header").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        compose.onNodeWithTag("catalog-header").assertIsNotDisplayed()
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        compose.onNodeWithTag("catalog-header").assertIsDisplayed()
        compose.onNodeWithText("测试模型 0").assertIsDisplayed()
        val top = compose.onNodeWithTag("catalog-header").fetchSemanticsNode().boundsInRoot.top
        assertEquals(initialTop, top, 1f)
        compose.runOnIdle { assertEquals(0, refreshes) }
        // 已在顶部时重复双击也不刷新或改变位置。
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        assertEquals(initialTop, compose.onNodeWithTag("catalog-header").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { assertEquals(0, refreshes) }
    }

    @Test fun scrollToTopHasIntermediateFramesInsteadOfJumping() = assertAnimatedScrollToTop(12)

    @Test fun distantScrollToTopAlsoAnimatesAndSettlesExactly() = assertAnimatedScrollToTop(300)

    private fun assertAnimatedScrollToTop(startIndex: Int) {
        lateinit var listState: LazyListState
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding, state, _ ->
                    listState = state
                    ModelListScreen(
                        uiState = ModelListUiState(
                            models = List(350) { ModelSummary("lab/model-$it", "测试模型 $it") },
                            isLoading = false,
                        ),
                        bottomOverlayPadding = bottomPadding,
                        listState = state,
                    )
                }
            }
        }
        compose.onNodeWithTag("model-list").performScrollToIndex(startIndex)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle {
            assertTrue("回顶必须经过中间滚动位置，而非瞬间跳到顶部", listState.firstVisibleItemIndex in 1 until startIndex)
            assertTrue("回顶应仍在播放滚动动画", listState.isScrollInProgress)
        }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle {
            assertEquals(0, listState.firstVisibleItemIndex)
            assertEquals(0, listState.firstVisibleItemScrollOffset)
            assertFalse(listState.isScrollInProgress)
        }
        compose.onNodeWithTag("catalog-header").assertIsDisplayed()
    }

    @Test fun singleClickAndSlowRepeatedClicksKeepCatalogPosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-models").performTouchInput { click() }
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("nav-models").performTouchInput {
            advanceEventTime(600)
            click()
            advanceEventTime(600)
            click()
        }
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("catalog-header").assertIsNotDisplayed()
    }

    @Test fun doubleClickModelsFromSettingsSelectsModelsAndScrollsToTop() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("catalog-header").assertIsDisplayed()
        compose.onNodeWithText("测试模型 0").assertIsDisplayed()
        assertIndicatorMatches("nav-models")
    }

    @Test fun doubleClickSettingsDoesNotResetCatalogPosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performTouchInput { doubleClick() }
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun settingsTapInterruptsConsecutiveModelTaps() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-models").performTouchInput { click() }
        compose.onNodeWithTag("nav-settings").performTouchInput {
            advanceEventTime(50)
            click()
        }
        compose.onNodeWithTag("nav-models").performTouchInput {
            advanceEventTime(50)
            click()
        }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun catalogScrollPositionSurvivesStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding, listState, _ ->
                    ModelListScreen(
                        ModelListUiState(
                            models = List(30) { ModelSummary("lab/model-$it", "测试模型 $it") },
                            isLoading = false,
                        ),
                        bottomOverlayPadding = bottomPadding,
                        listState = listState,
                    )
                }
            }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        compose.onNodeWithTag("catalog-header").assertIsDisplayed()
    }

    @Test fun navigationDragAndFollowingTapDoNotTriggerScrollToTop() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        dragNavigationToSettings(duration = 100)
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("nav-models").performTouchInput { click() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertEquals(before, compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertIndicatorMatches("nav-models")
    }

    @Test fun doubleClickPreservesExpandedPriceDetails() {
        show(models = listOf(tieredModel) + List(20) { ModelSummary("lab/model-$it", "测试模型 $it") })
        compose.onNodeWithText("API 价格").performClick()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        compose.onNodeWithText("上下文 ≥ 100 tokens：输入 $0.20 · 输出 $0.30").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun scrollToTopIsAvailableAsAccessibilityAction() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performCustomAccessibilityActionWithLabel("回到模型列表顶部")
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("catalog-header").assertIsDisplayed()
    }

    @Test fun doubleClickModelsSupportsDarkThemeLargeFontAndRtl() {
        show(darkTheme = true, fontScale = 2f, direction = LayoutDirection.Rtl)
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performTouchInput { doubleClick() }
        compose.onNodeWithTag("catalog-header").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertIndicatorMatches("nav-models")
    }

    @Test fun selectedTabAndPriceExpansionSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding, listState, _ ->
                    ModelListScreen(
                        uiState = ModelListUiState(models = listOf(tieredModel), isLoading = false),
                        bottomOverlayPadding = bottomPadding,
                        listState = listState,
                    )
                }
            }
        }
        compose.onNodeWithText("API 价格").performClick()
        compose.onNodeWithText("上下文 ≥ 100 tokens：输入 $0.20 · 输出 $0.30").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithText("数据来源与许可").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").performClick()
        compose.onNodeWithText("上下文 ≥ 100 tokens：输入 $0.20 · 输出 $0.30").assertIsDisplayed()
    }

    @Test fun lastModelCanScrollFullyAboveFloatingNavigation() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/model-29"))
        compose.onNodeWithTag("model-list").performTouchInput { swipeUp() }
        val lastModel = compose.onNodeWithTag("model:lab/model-29").fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Last model must remain readable above navigation", lastModel.bottom <= navigation.top)
    }

    @Test fun settingsOpensStandaloneSourcePageWithPricesAndBundledLicense() {
        show()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithTag("data-source-screen").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
        compose.onNodeWithText("本应用为独立第三方", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("data-source-content").performScrollToNode(hasText(priceNotice))
        compose.onNodeWithText(priceNotice).assertIsDisplayed()
        compose.onAllNodesWithText(priceNotice).assertCountEquals(1)
        compose.onNodeWithTag("data-source-content").performScrollToNode(hasText("Copyright (c) 2025 models.dev", substring = true))
        compose.onNodeWithText("Copyright (c) 2025 models.dev", substring = true).assertIsDisplayed()
        compose.onNodeWithText("THE SOFTWARE IS PROVIDED", substring = true).assertExists()
        compose.onNodeWithTag("data-source-content").performScrollToNode(hasText("Benchmark 数据来源与许可"))
        compose.onNodeWithText("Benchmark 数据来源与许可").assertIsDisplayed()
        compose.onNodeWithTag("data-source-content").performScrollToNode(hasText("Copyright (c) 2023 DeepSeek", substring = true))
        compose.onNodeWithText("Copyright (c) 2023 DeepSeek", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
        compose.onNodeWithTag("data-source-screen").assertDoesNotExist()
    }

    @Test fun sourcePageSystemBackPreservesSelectedTabAndCatalogPosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("测试模型 12"))
        val before = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithTag("data-source-screen").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("nav-models").performClick()
        val after = compose.onNodeWithText("测试模型 12").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
    }

    @Test fun sourcePageAndScrollPositionSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding, listState, _ ->
                    ModelListScreen(
                        ModelListUiState(isLoading = false),
                        bottomOverlayPadding = bottomPadding,
                        listState = listState,
                    )
                }
            }
        }
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithTag("data-source-content").performScrollToNode(hasText("Models.dev MIT 许可"))
        val before = compose.onNodeWithText("Models.dev MIT 许可").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("data-source-screen").assertIsDisplayed()
        val after = compose.onNodeWithText("Models.dev MIT 许可").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test fun sourcePageSupportsDarkThemeLargeFontAndRtl() {
        show(darkTheme = true, fontScale = 2f, direction = LayoutDirection.Rtl)
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        for (text in listOf("数据来源与许可", priceNotice)) {
            if (text == priceNotice) {
                compose.onNodeWithTag("data-source-content").performScrollToNode(hasText(text))
            }
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("Text must not clip vertically", layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse(layout.isLineEllipsized(line))
            }
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test fun headerNoticeSurvivesTabSwitchAndStillAppliesUpdate() {
        var applies = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding, listState, _ ->
                    ModelListScreen(
                        uiState = ModelListUiState(
                            models = listOf(ModelSummary("lab/cached", "本地模型")),
                            isLoading = false,
                            updateAvailable = true,
                        ),
                        bottomOverlayPadding = bottomPadding,
                        listState = listState,
                        onApplyUpdate = { applies++ },
                    )
                }
            }
        }
        val action = compose.onNodeWithText("立即更新")
        action.assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("立即更新").assertIsNotDisplayed()
        compose.onNodeWithTag("nav-models").performClick()
        compose.onNodeWithTag("catalog-notice").assertIsDisplayed()
        val header = compose.onNodeWithTag("catalog-header").fetchSemanticsNode().boundsInRoot
        val notice = compose.onNodeWithTag("catalog-notice").fetchSemanticsNode().boundsInRoot
        assertTrue("Notice must be inline below header", notice.top >= header.bottom)
        val navigation = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(action.fetchSemanticsNode().boundsInRoot.bottom <= navigation.top)
        action.performClick()
        compose.runOnIdle { assertEquals(1, applies) }
    }

    @Test fun largeFontAndRtlKeepLabelsAndIndicatorUsable() {
        show(fontScale = 2f, direction = LayoutDirection.Rtl)
        assertIndicatorMatches("nav-models")
        for ((tag, label) in listOf("nav-models" to "模型", "nav-settings" to "设置")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertTrue(results.isNotEmpty())
            val layout = results.single()
            // 与列表回归一致，按实际行边界检查；紧凑 Text 的 paragraph 宽度可导致溢出标记误报。
            assertFalse("$label height overflow", layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue("$label clips at start", layout.getLineLeft(line) >= -1f)
                assertTrue("$label clips at end", layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse("$label is ellipsized", layout.isLineEllipsized(line))
            }
            assertEquals(label.length, layout.getLineEnd(layout.lineCount - 1))
        }
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        assertIndicatorMatches("nav-settings")
        compose.onNodeWithText("数据来源与许可").assertIsDisplayed()
    }

    @Test fun darkThemeKeepsBothTabsUsable() {
        show(darkTheme = true)
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        assertIndicatorMatches("nav-settings")
        compose.onNodeWithText("数据来源与许可").assertIsDisplayed()
    }

    private fun dragNavigationToSettings(duration: Long) {
        val bar = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("nav-models").fetchSemanticsNode().boundsInRoot.center - bar.topLeft
        val target = compose.onNodeWithTag("nav-settings").fetchSemanticsNode().boundsInRoot.center - bar.topLeft
        compose.onNodeWithTag("glass-navigation").performTouchInput { swipe(start, target, duration) }
    }

    private fun assertIndicatorMatches(tabTag: String) {
        val indicator = compose.onNodeWithTag("nav-indicator").fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithTag(tabTag).fetchSemanticsNode().boundsInRoot
        assertEquals(tab.center.x, indicator.center.x, 1f)
        // 三栏 weight 分配整像素时可相差 1px，另留极小浮点变换误差。
        assertEquals(tab.width, indicator.width, 1.01f)
    }

    private fun show(
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        darkTheme: Boolean = false,
        models: List<ModelSummary> = List(30) { ModelSummary("lab/model-$it", "测试模型 $it") },
        onRefresh: () -> Unit = {},
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ModelAtlasScaffold { bottomPadding, listState, _ ->
                        ModelListScreen(
                            uiState = ModelListUiState(
                                models = models,
                                isLoading = false,
                            ),
                            bottomOverlayPadding = bottomPadding,
                            listState = listState,
                            onRefresh = onRefresh,
                        )
                    }
                }
            }
        }
    }

    private val tieredModel = ModelSummary(
        "lab/tiered", "阶梯报价模型",
        price = ModelPriceSummary(
            providerId = "lab",
            providerName = "Lab",
            isOfficial = true,
            input = 0.1,
            output = 0.2,
            tiers = listOf(ModelPriceTierSummary(
                conditionType = "context", conditionSizeTokens = 100, input = 0.2, output = 0.3,
            )),
        ),
    )

    private val priceNotice = "价格为上游目录中某个服务商对该模型服务的报价，口径为 USD / 百万 tokens；同一模型的不同服务商价格不可直接比较，缓存、档位、模式与订阅等适用条件以服务商说明为准。"
}
