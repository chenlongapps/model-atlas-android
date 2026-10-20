package io.github.chenlongapps.modelatlas.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

    @Test fun tabsSwitchBetweenRealCatalogAndSettingsPlaceholder() {
        show()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithText("测试模型 0").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        compose.onNodeWithText("暂无设置项").assertIsDisplayed()
        compose.onNodeWithTag("model-list").assertIsNotDisplayed()
        compose.onNodeWithTag("nav-models").performClick().assertIsSelected()
        compose.onNodeWithText("测试模型 0").assertIsDisplayed()
    }

    @Test fun pageSwipeAndNavigationDragKeepSelectionAndIndicatorInSync() {
        show()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        assertIndicatorMatches("nav-settings")
        compose.onNodeWithTag("main-pager").performTouchInput { swipeRight() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertIndicatorMatches("nav-models")
        compose.onNodeWithTag("glass-navigation").performTouchInput {
            swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 500)
        }
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

    @Test fun selectedTabAndPriceExpansionSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding ->
                    ModelListScreen(
                        uiState = ModelListUiState(models = listOf(tieredModel), isLoading = false),
                        bottomOverlayPadding = bottomPadding,
                    )
                }
            }
        }
        compose.onNodeWithText("API 价格").performClick()
        compose.onNodeWithText("上下文 ≥ 100 tokens：输入 $0.20 · 输出 $0.30").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithText("暂无设置项").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").performClick()
        compose.onNodeWithText("上下文 ≥ 100 tokens：输入 $0.20 · 输出 $0.30").assertIsDisplayed()
    }

    @Test fun catalogFooterCanScrollFullyAboveFloatingNavigation() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("关于这些价格"))
        // 滚到底部，不仅让标题进入视口，也验证整个价格说明不被悬浮栏挡住。
        compose.onNodeWithTag("model-list").performScrollToNode(hasText(priceNotice))
        compose.onNodeWithTag("model-list").performTouchInput { swipeUp() }
        val footer = compose.onNodeWithText(priceNotice).fetchSemanticsNode().boundsInRoot
        val navigation = compose.onNodeWithTag("glass-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Footer must remain readable above navigation", footer.bottom <= navigation.top)
    }

    @Test fun updateSnackbarStaysAboveNavigationAndStillAppliesUpdate() {
        var applies = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { bottomPadding ->
                    ModelListScreen(
                        uiState = ModelListUiState(
                            models = listOf(ModelSummary("lab/cached", "本地模型")),
                            isLoading = false,
                            updateAvailable = true,
                        ),
                        bottomOverlayPadding = bottomPadding,
                        onApplyUpdate = { applies++ },
                    )
                }
            }
        }
        val action = compose.onNodeWithText("立即更新")
        action.assertIsDisplayed()
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
        compose.onNodeWithText("暂无设置项").assertIsDisplayed()
    }

    @Test fun darkThemeKeepsBothTabsUsable() {
        show(darkTheme = true)
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        assertIndicatorMatches("nav-settings")
        compose.onNodeWithText("暂无设置项").assertIsDisplayed()
    }

    private fun assertIndicatorMatches(tabTag: String) {
        val indicator = compose.onNodeWithTag("nav-indicator").fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithTag(tabTag).fetchSemanticsNode().boundsInRoot
        assertEquals(tab.center.x, indicator.center.x, 1f)
        assertEquals(tab.width, indicator.width, 1f)
    }

    private fun show(
        fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr,
        darkTheme: Boolean = false,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ModelAtlasScaffold { bottomPadding ->
                        ModelListScreen(
                            uiState = ModelListUiState(
                                models = List(30) { ModelSummary("lab/model-$it", "测试模型 $it") },
                                isLoading = false,
                            ),
                            bottomOverlayPadding = bottomPadding,
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
