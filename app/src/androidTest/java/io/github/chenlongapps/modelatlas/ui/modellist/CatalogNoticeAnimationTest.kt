package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CatalogNoticeAnimationTest {
    @get:Rule val compose = createComposeRule()

    private val idle = ModelListUiState(
        isLoading = false,
        models = listOf(ModelSummary("lab/cached", "本地模型")),
    )

    @Test fun lightNoticeFadesAndResizesWithoutLeavingAGap() = assertAppearanceAndExit(darkTheme = false)

    @Test fun darkNoticeFadesAndResizesWithoutLeavingAGap() = assertAppearanceAndExit(darkTheme = true)

    private fun assertAppearanceAndExit(darkTheme: Boolean) {
        val state = show(idle, darkTheme)
        val originalModelTop = modelTop()
        assertEquals(0f, transitionHeight(), 0.5f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = idle.copy(updateAvailable = true) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val enteringHeight = transitionHeight()
        val enteringTop = modelTop()
        val enteringColor = noticeBackgroundPixel()
        assertTrue("Notice must expand rather than appear at full height", enteringHeight > 0f)
        assertTrue("Following models must move down with the expansion", enteringTop > originalModelTop)

        compose.mainClock.advanceTimeBy(250)
        val visibleHeight = transitionHeight()
        val visibleTop = modelTop()
        val visibleColor = noticeBackgroundPixel()
        assertTrue("Height must still be intermediate during entry", enteringHeight < visibleHeight)
        assertTrue("List movement must be intermediate during entry", enteringTop < visibleTop)
        assertNotEquals("Entry must also fade the painted surface", visibleColor, enteringColor)
        compose.onNodeWithText("立即更新").assertIsDisplayed()

        compose.runOnIdle { state.value = idle }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val exitingHeight = transitionHeight()
        assertTrue("Exit must retain intermediate height", exitingHeight > 0f && exitingHeight < visibleHeight)
        assertTrue("Models must move up gradually", modelTop() > originalModelTop && modelTop() < visibleTop)
        assertNotEquals("Exit must fade the painted surface", visibleColor, noticeBackgroundPixel())
        compose.onNodeWithText("立即更新").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(250)
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        assertEquals("The notice and its spacing must fully collapse", 0f, transitionHeight(), 0.5f)
        assertEquals("Models must return to their original position", originalModelTop, modelTop(), 0.5f)
    }

    @Test fun errorToPendingUpdateCrossfadesAndAnimatesHeight() {
        val state = show(idle.copy(refreshError = ModelListError.NETWORK, updateAvailable = true))
        val errorHeight = transitionHeight()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("关闭").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(80)
        val changingHeight = transitionHeight()
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertDoesNotExist()
        compose.onNodeWithText("发现新数据，可更新模型目录").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(250)
        val pendingHeight = transitionHeight()
        assertTrue("The replacement must interpolate its height", changingHeight < errorHeight && changingHeight > pendingHeight)
        compose.onAllNodes(hasTestTag("catalog-notice")).assertCountEquals(1)
        compose.onNodeWithText("立即更新").assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.value.updateAvailable) }
    }

    @Test fun refreshAndNewResultCanInterruptAnExitWithoutLeavingOldContent() {
        val state = show(idle.copy(updateAvailable = true))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = idle.copy(isRefreshing = true) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { state.value = idle.copy(message = ModelListMessage.UPDATED) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("模型目录已更新").assertIsDisplayed()
        compose.onNodeWithText("立即更新").assertDoesNotExist()
        compose.onAllNodes(hasTestTag("catalog-notice")).assertCountEquals(1)
        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        assertEquals(0f, transitionHeight(), 0.5f)
    }

    private fun show(initial: ModelListUiState, darkTheme: Boolean = false): MutableState<ModelListUiState> {
        val state = mutableStateOf(initial)
        compose.setContent {
            CompositionLocalProvider(LocalAccessibilityManager provides null) {
                ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ModelListScreen(
                        uiState = state.value,
                        modifier = Modifier.width(320.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        onDismissNotice = { state.value = state.value.copy(message = null, refreshError = null) },
                    )
                }
            }
        }
        return state
    }

    private fun transitionHeight(): Float {
        val bounds = compose.onNodeWithTag("catalog-notice-transition").getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    private fun modelTop(): Float = compose.onNodeWithTag("model:lab/cached").getUnclippedBoundsInRoot().top.value

    private fun noticeBackgroundPixel(): Color {
        val pixels = compose.onNodeWithTag("catalog-notice-transition").captureToImage().toPixelMap()
        // 避开文字、图标和圆角，在 16dp 间距后的卡片顶部留白取样。
        val y = with(compose.density) { 20.dp.roundToPx() }
        assertTrue("The in-flight notice must have visible card content", pixels.height > y)
        return pixels[pixels.width / 2, y]
    }
}

class CatalogNoticeReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun disabledAnimationsImmediatelyApplyTheFinalLayout() {
        val notice = mutableStateOf<CatalogNotice?>(null)
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                AnimatedCatalogStatusNotice(notice.value, {}, {}, {})
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { notice.value = CatalogNotice(updateAvailable = true) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithText("立即更新").assertIsDisplayed()
        compose.runOnIdle { notice.value = null }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        val bounds = compose.onNodeWithTag("catalog-notice-transition").getUnclippedBoundsInRoot()
        assertEquals(0f, (bounds.bottom - bounds.top).value, 0.5f)
    }
}
