package io.github.chenlongapps.modelatlas.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.debug.DebugDeviceInfo
import io.github.chenlongapps.modelatlas.ui.debug.DebugScreen
import io.github.chenlongapps.modelatlas.ui.debug.DebugUiState
import io.github.chenlongapps.modelatlas.ui.modelcompare.CompareModelState
import io.github.chenlongapps.modelatlas.ui.modelcompare.CompareReadStatus
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareScreen
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareUiState
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailScreen
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailStatus
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailUiState
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationScrollTest {
    @get:Rule val compose = createComposeRule()
    private val touchExploration = mutableStateOf(false)
    private lateinit var listState: LazyListState
    private var bottomPadding = Dp.Unspecified
    private val models = List(30) { ModelSummary("lab/$it", "Model $it") }

    @Test fun catalogHidesAndRevealsWithoutChangingViewportOrBottomPadding() {
        show()
        val padding = bottomPadding
        val viewport = compose.onNodeWithTag("model-list").fetchSemanticsNode().boundsInRoot
        scroll("model-list", forward = true)
        assertHidden()
        assertEquals(viewport, compose.onNodeWithTag("model-list").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertEquals(padding, bottomPadding) }
        scroll("model-list", forward = false)
        assertShown()
        compose.runOnIdle { assertEquals(padding, bottomPadding) }
    }

    @Test fun visibilityAnimationHasIntermediateFramesAndCanReverseBeforeCompletion() {
        show()
        val initialTop = compose.onNodeWithTag("glass-navigation-motion").fetchSemanticsNode().boundsInRoot.top
        val viewportBottom = compose.onNodeWithTag("model-list").fetchSemanticsNode().boundsInRoot.bottom
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("model-list").performTouchInput {
            // 完整慢拖会推进测试时钟 600ms，故分段注入并保留手指，以观察 200ms 过渡的中间帧。
            down(center)
            moveBy(Offset(0f, -100f), delayMillis = 16)
        }
        compose.mainClock.advanceTimeBy(80)
        val middleTop = compose.onNodeWithTag("glass-navigation-motion").fetchSemanticsNode().boundsInRoot.top
        assertTrue("底栏应经过中间位移而不是瞬间消失", middleTop > initialTop && middleTop < viewportBottom)
        compose.onNodeWithTag("model-list").performTouchInput {
            moveBy(Offset(0f, 100f), delayMillis = 16)
            up()
        }
        compose.mainClock.advanceTimeBy(320)
        assertShown()
        assertEquals(initialTop, compose.onNodeWithTag("glass-navigation-motion").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test fun programmaticScrollDoesNotHideAndHorizontalSwitchRestoresNavigationAndPosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToIndex(12)
        assertShown()
        scroll("model-list", forward = true)
        assertHidden()
        val position = position()
        compose.onNodeWithTag("main-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("nav-compare").assertIsSelected()
        assertShown()
        compose.onNodeWithTag("nav-models").performClick()
        compose.runOnIdle { assertEquals(position, position()) }
    }

    @Test fun comparisonAndDebugUseTheSameScrollBehavior() {
        show()
        compose.onNodeWithTag("nav-compare").performClick()
        scroll("compare-list", forward = true)
        assertHidden()
        scroll("compare-list", forward = false)
        assertShown()
        compose.onNodeWithTag("nav-debug").performClick()
        scroll("debug-content", forward = true)
        assertHidden()
        scroll("debug-content", forward = false)
        assertShown()
    }

    @Test fun settingsHidesOnlyWhenItActuallyScrollsInAShortWindow() {
        show(shortWindow = true)
        compose.onNodeWithTag("nav-settings").performClick()
        scroll("settings-screen", forward = true, distance = 90f)
        assertHidden()
        scroll("settings-screen", forward = false, distance = 90f)
        assertShown()
    }

    @Test fun shortSettingsAndTopPullDoNotHideNavigation() {
        show()
        scroll("model-list", forward = false)
        assertShown()
        compose.onNodeWithTag("nav-settings").performClick()
        scroll("settings-screen", forward = true)
        assertShown()
    }

    @Test fun pickerScrollingDoesNotHideUnderlyingNavigation() {
        show()
        compose.onNodeWithTag("nav-compare").performClick()
        compose.onNodeWithTag("compare-change-0", useUnmergedTree = true).performClick()
        scroll("compare-picker-list", forward = true)
        pressBack()
        assertShown()
    }

    @Test fun detailScrollingAndReturnPreserveHiddenNavigationAndCatalogPosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToIndex(12)
        scroll("model-list", forward = true)
        assertHidden()
        val position = position()
        var sourceTag = ""
        compose.runOnIdle {
            sourceTag = listState.layoutInfo.visibleItemsInfo.first { it.offset >= 0 }.key.toString()
        }
        compose.onNodeWithTag(sourceTag).performClick()
        scroll("model-detail-scroll", forward = true)
        pressBack()
        assertHidden()
        compose.runOnIdle { assertEquals(position, position()) }
        scroll("model-list", forward = false)
        assertShown()
    }

    @Test fun enablingTouchExplorationRevealsAndKeepsNavigationAvailable() {
        show()
        scroll("model-list", forward = true)
        assertHidden()
        compose.runOnIdle { touchExploration.value = true }
        assertShown()
        scroll("model-list", forward = true)
        assertShown()
        compose.runOnIdle { touchExploration.value = false }
        scroll("model-list", forward = true)
        assertHidden()
    }

    @Test fun restorationKeepsCatalogPositionButStartsWithVisibleNavigation() {
        val restoration = StateRestorationTester(compose)
        show(restoration = restoration)
        compose.onNodeWithTag("model-list").performScrollToIndex(12)
        scroll("model-list", forward = true)
        assertHidden()
        val position = position()
        restoration.emulateSavedInstanceStateRestore()
        assertShown()
        compose.runOnIdle { assertEquals(position, position()) }
    }

    @Test fun darkLargeFontRtlNavigationStillHidesCompletelyAndReveals() {
        show(largeRtl = true)
        scroll("model-list", forward = true)
        assertHidden()
        scroll("model-list", forward = false)
        assertShown()
    }

    private fun scroll(tag: String, forward: Boolean, distance: Float = 200f) {
        compose.onNodeWithTag(tag).performTouchInput {
            // 慢速短拖，避免快速 fling 混淆方向判断；不触及底栏或系统手势区。
            val dy = (if (forward) -1 else 1) * distance.coerceAtMost(height * .35f)
            swipe(center, center + Offset(0f, dy), 600)
        }
    }

    private fun assertHidden() {
        compose.onNodeWithTag("nav-models").assertDoesNotExist()
        compose.onNodeWithTag("glass-navigation-motion").assertIsNotDisplayed()
    }

    private fun assertShown() {
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").assertExists()
    }

    private fun position() = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset

    private fun show(
        shortWindow: Boolean = false,
        largeRtl: Boolean = false,
        restoration: StateRestorationTester? = null,
    ) {
        val content: @Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (largeRtl) 2f else 1f),
                LocalLayoutDirection provides if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ModelAtlasTheme(darkTheme = largeRtl, dynamicColor = false) {
                    // 模拟小窗口的绘制边界；不能让滑出该窗口的内容仍画在测试 Activity 余下区域。
                    Box(if (shortWindow) Modifier.height(320.dp).clipToBounds() else Modifier) {
                        ModelAtlasScaffold(
                            touchExplorationEnabled = touchExploration.value,
                            compareContent = { padding, visible ->
                                val details = models.take(2).map { ModelDetail(it.id, it.name, fetchedAtEpochMillis = 0) }
                                ModelCompareScreen(ModelCompareUiState(models, CompareReadStatus.CONTENT,
                                    CompareModelState(details[0].id, CompareReadStatus.CONTENT, details[0]),
                                    CompareModelState(details[1].id, CompareReadStatus.CONTENT, details[1])),
                                    bottomOverlayPadding = padding, isVisible = visible)
                            },
                            debugContent = { padding, _, _ ->
                                DebugScreen(DebugUiState(), DebugDeviceInfo("test", 1, "test", true, "test", "test", "test", 37),
                                    onReadStatistics = {}, onRefresh = {}, bottomOverlayPadding = padding)
                            },
                            detailContent = { id, back ->
                                ModelDetailScreen(ModelDetailUiState(id, ModelDetailStatus.CONTENT,
                                    ModelDetail(id, "Detail", fetchedAtEpochMillis = 0)), onBack = back)
                            },
                        ) { padding, state, open ->
                            listState = state
                            bottomPadding = padding
                            ModelListScreen(ModelListUiState(models = models, isLoading = false),
                                bottomOverlayPadding = padding, listState = state, onModelClick = open)
                        }
                    }
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }
}

class NavigationScrollReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun disabledAnimationsStillHideAndRevealNavigation() {
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { padding, state, _ ->
                    ModelListScreen(ModelListUiState(models = List(20) { ModelSummary("lab/$it", "Model $it") }, isLoading = false),
                        bottomOverlayPadding = padding, listState = state)
                }
            }
        }
        compose.onNodeWithTag("model-list").performTouchInput { swipe(center, center - Offset(0f, 200f), 600) }
        compose.onNodeWithTag("glass-navigation-motion").assertIsNotDisplayed()
        compose.onNodeWithTag("nav-models").assertDoesNotExist()
        compose.onNodeWithTag("model-list").performTouchInput { swipe(center, center + Offset(0f, 200f), 600) }
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
    }
}
