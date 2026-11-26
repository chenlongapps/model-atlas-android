package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelBackTestInput
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class AssistantRevealNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun enterAndExitClipOnlyAssistantAroundRealAiButtonAndRetainMain() {
        var layoutsCreated = 0
        var layoutsDisposed = 0
        var closes = 0
        lateinit var back: () -> Unit
        var compactRtl by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (compactRtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                RevealContent(
                    modifier = if (compactRtl) Modifier.size(320.dp, 300.dp) else Modifier.fillMaxSize(),
                    onClosed = { closes++ },
                    onBackAvailable = { back = it },
                    onMainChange = { if (it) layoutsCreated++ else layoutsDisposed++ },
                )
            }
        }
        for (variant in 0..1) {
            if (variant == 1) compose.runOnIdle { compactRtl = true }
            val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
            val host = compose.onNodeWithTag("reveal-host").fetchSemanticsNode().boundsInRoot
            val localAi = ai.translate(-host.topLeft)
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("nav-chat").performClick()
            compose.mainClock.advanceTimeBy(96)
            assertPartialFrame(frame(), localAi)
            compose.onNodeWithTag("main-pager").assertDoesNotExist()
            compose.mainClock.advanceTimeBy(460)
            assertCorners(ChatColor)

            compose.runOnIdle { back(); back() }
            compose.mainClock.advanceTimeBy(220)
            assertPartialFrame(frame(), localAi)
            compose.onNodeWithTag("main-pager").assertDoesNotExist()
            compose.runOnIdle { assertEquals(variant + 1, closes) }
            compose.mainClock.advanceTimeBy(220)
            assertCorners(MainColor)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("nav-chat").assertExists()
        }
        compose.runOnIdle { assertEquals(1, layoutsCreated); assertEquals(0, layoutsDisposed) }
    }

    @Test fun repeatedTapsAtTheAiButtonReverseTheClipAndCloseTheFullyOpenAssistant() {
        var mounts = 0
        var disposals = 0
        var closes = 0
        compose.setContent {
            RevealContent(
                onClosed = { closes++ },
                onAssistantChange = { if (it) mounts++ else disposals++ },
            )
        }
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("reveal-host")
        val localAi = ai.translate(-host.fetchSemanticsNode().boundsInRoot.topLeft)
        fun tap() = host.performTouchInput { click(localAi.center) }
        fun clipTop(): Int {
            val pixels = frame()
            return (0 until localAi.top.toInt()).first { pixels[localAi.center.x.toInt(), it] == ChatColor }
        }

        compose.mainClock.autoAdvance = false
        tap()
        compose.mainClock.advanceTimeBy(160)
        assertCorners(MainColor)
        val back = compose.onNodeWithTag("chat-exit").assertIsEnabled()
            .assertContentDescriptionEquals("退出助手")
        assertEquals(ai, back.fetchSemanticsNode().boundsInRoot)
        val openingEdge = clipTop()

        tap()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("nav-chat").assertExists()
        compose.onNodeWithTag("nav-models").assertDoesNotExist()
        compose.onNodeWithTag("reveal-assistant").assertDoesNotExist()
        val closingStart = clipTop()
        assertTrue("反向收缩应接续当前边缘", abs(closingStart - openingEdge) < localAi.top / 3)
        compose.mainClock.advanceTimeBy(48)
        val closingEdge = clipTop()
        assertTrue("打开途中点击后应开始收缩：$closingStart → $closingEdge", closingEdge > closingStart)

        tap()
        compose.mainClock.advanceTimeBy(32)
        back.assertIsEnabled()
        val reopenStart = clipTop()
        assertTrue("重新展开应接续当前边缘", abs(reopenStart - closingEdge) < localAi.top / 3)
        compose.mainClock.advanceTimeBy(96)
        val reopenedEdge = clipTop()
        assertTrue("收缩途中点击后应重新展开：$reopenStart → $reopenedEdge", reopenedEdge in 1 until reopenStart)
        compose.mainClock.advanceTimeBy(460)
        assertCorners(ChatColor)
        back.assertIsEnabled()
        compose.onNodeWithTag("chat-send").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closes); assertEquals(1, mounts); assertEquals(0, disposals) }

        tap()
        compose.mainClock.advanceTimeBy(440)
        assertCorners(MainColor)
        compose.onNodeWithTag("nav-models").assertExists()
        compose.onNodeWithTag("reveal-assistant").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, closes); assertEquals(1, disposals) }
    }

    @Test fun predictiveProgressCanPauseCancelAndCommitWithoutReplayingEntrance() {
        val input = ModelBackTestInput()
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var showContent by mutableStateOf(true)
        lateinit var saved: Map<String, List<Any?>>
        var closes = 0
        compose.setContent {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.removeInput(input) }
            }
            if (showContent) {
                CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                    RevealContent(onClosed = { closes++ })
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-chat").performClick()
        compose.mainClock.advanceTimeBy(96)
        assertCorners(MainColor)

        fun recreate() {
            // 使用暂停的时钟分别提交销毁和恢复，保持 rememberSaveable 调用点不变。
            compose.runOnIdle { saved = registry.performSave(); showContent = false }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithTag("reveal-host").assertDoesNotExist()
            compose.runOnIdle { registry = SaveableStateRegistry(saved) { true }; showContent = true }
            compose.mainClock.advanceTimeBy(48)
        }

        recreate()
        assertCorners(ChatColor)

        compose.runOnIdle { input.start(); input.progress(.75f) }
        compose.mainClock.advanceTimeBy(64)
        assertCorners(MainColor)
        compose.mainClock.advanceTimeBy(600)
        assertCorners(MainColor)
        compose.runOnIdle { input.cancel(); assertEquals(0, closes) }
        compose.mainClock.advanceTimeBy(460)
        assertCorners(ChatColor)

        compose.runOnIdle { input.start(); input.progress(.5f) }
        compose.mainClock.advanceTimeBy(48)
        compose.runOnIdle { input.complete(); assertEquals(1, closes) }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("main-pager").assertDoesNotExist()
        recreate()
        assertCorners(MainColor)
        compose.onNodeWithTag("reveal-assistant").assertDoesNotExist()
        compose.onNodeWithTag("nav-chat").assertExists()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    private fun frame() = compose.onNodeWithTag("reveal-host").captureToImage().toPixelMap()

    private fun assertCorners(color: Color) {
        val pixels = frame()
        assertEquals(color, pixels[4, 4])
        assertEquals(color, pixels[pixels.width - 5, 4])
    }

    private fun assertPartialFrame(pixels: PixelMap, ai: Rect) {
        val x = ai.center.x.toInt()
        val y = ai.top.toInt() - 12
        assertEquals("按钮正上方已经揭示助手", ChatColor, pixels[x, y])
        assertEquals("对角仍是原页面", MainColor, pixels[if (x > pixels.width / 2) 4 else pixels.width - 5, 4])
        // 再抬高采样，避开底栏玻璃表面的外扩效果。
        assertEquals("圆心另一侧仍是原页面", MainColor, pixels[pixels.width - 1 - x, y - 60])
    }
}

class AssistantRevealReducedMotionTest {
    @get:Rule val compose = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    @Test fun openAndCloseSettleOnFirstAnimationFrame() {
        compose.setContent { RevealContent() }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-chat").performClick()
        compose.mainClock.advanceTimeBy(64)
        assertEquals(ChatColor, compose.onNodeWithTag("reveal-host").captureToImage().toPixelMap()[4, 4])
        compose.onNodeWithTag("chat-exit").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(64)
        assertEquals(MainColor, compose.onNodeWithTag("reveal-host").captureToImage().toPixelMap()[4, 4])
        compose.onNodeWithTag("reveal-assistant").assertDoesNotExist()
    }
}

private val MainColor = Color(0xFF60431A)
private val ChatColor = Color(0xFF2153AE)

@Composable
private fun RevealContent(
    modifier: Modifier = Modifier.fillMaxSize(),
    onClosed: () -> Unit = {},
    onBackAvailable: (() -> Unit) -> Unit = {},
    onMainChange: (Boolean) -> Unit = {},
    onAssistantChange: (Boolean) -> Unit = {},
) {
    ModelAtlasTheme(dynamicColor = false) {
        Box(modifier.testTag("reveal-host")) {
            ModelAtlasScaffold(
                onChatClosed = onClosed,
                chatContent = { _, _, onBack ->
                    onBackAvailable(onBack)
                    DisposableEffect(Unit) {
                        onAssistantChange(true)
                        onDispose { onAssistantChange(false) }
                    }
                    Box(Modifier.fillMaxSize().background(ChatColor).testTag("reveal-assistant"))
                },
            ) { _, _, _ ->
                DisposableEffect(Unit) {
                    onMainChange(true)
                    onDispose { onMainChange(false) }
                }
                Box(Modifier.fillMaxSize().background(MainColor))
            }
        }
    }
}
