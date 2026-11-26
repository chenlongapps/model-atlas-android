package io.github.chenlongapps.modelatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.chenlongapps.modelatlas.testing.saveRegressionScreenshot
import io.github.chenlongapps.modelatlas.ui.chat.ChatUiState
import io.github.chenlongapps.modelatlas.ui.navigation.BottomBarMode
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationItem
import io.github.chenlongapps.modelatlas.ui.navigation.MorphingBottomBar
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** Debug 测试宿主显式传 Release 的四项胶囊，避免只验证带调试条目的布局。 */
class GlassNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun releaseNavigationAt320DpKeepsCircleAndFourTabsUsable() = assertReleaseNavigation(false)

    @Test fun releaseNavigationAt320DpLargeFontDarkRtlKeepsCircleAndFourTabsUsable() = assertReleaseNavigation(true)

    @Test fun lensTravelsBetweenTabsWhileHeldAndCommitsOnlyOnRelease() {
        val requests = mutableListOf<Int>()
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ReleaseBar(onNavigate = { requests.add(it) })
            }
        }
        val capsule = compose.onNodeWithTag("glass-navigation-capsule")
        val capsuleBounds = capsule.fetchSemanticsNode().boundsInRoot
        val models = compose.onNodeWithTag("nav-models")
        val compare = compose.onNodeWithTag("nav-compare")
        val start = models.fetchSemanticsNode().boundsInRoot.center - capsuleBounds.topLeft
        val target = compare.fetchSemanticsNode().boundsInRoot.center - capsuleBounds.topLeft
        val betweenTabs = start + (target - start) * 0.70f
        capsule.performTouchInput {
            down(start)
            moveTo(betweenTabs, delayMillis = 150)
        }
        models.assertIsSelected()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        val indicator = compose.onNodeWithTag("nav-indicator")
        assertEquals(betweenTabs.x + capsuleBounds.left, indicator.fetchSemanticsNode().boundsInRoot.center.x, 2f)
        saveRegressionScreenshot("navigation-lens-drag")

        capsule.performTouchInput {
            moveTo(target - Offset((target.x - start.x) * 0.09f, 0f), delayMillis = 100)
            up()
        }
        compare.assertIsSelected()
        assertEquals(compare.fetchSemanticsNode().boundsInRoot.center.x, indicator.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.runOnIdle { assertEquals(listOf(2), requests) }
    }

    @Test fun latestTapWinsDuringPagerAnimation() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ReleaseBar() } }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-settings").performClick()
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("nav-benchmarks").performClick()
        compose.onNodeWithTag("nav-compare").performClick()
        compose.mainClock.autoAdvance = true

        val target = compose.onNodeWithTag("nav-compare").assertIsSelected().fetchSemanticsNode().boundsInRoot
        val lens = compose.onNodeWithTag("nav-indicator").fetchSemanticsNode().boundsInRoot
        assertEquals(target.center.x, lens.center.x, 1f)
    }

    @Test fun assistantMorphKeepsAnInFlightNavigationRequest() {
        var mode by mutableStateOf(BottomBarMode.Navigation)
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ReleaseBar(
                    mode = mode,
                    onNavigate = { repeat(30) { withFrameNanos {} } },
                    onOpenChat = { mode = BottomBarMode.Chat },
                )
            }
        }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("nav-settings").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("nav-chat").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.runOnIdle { mode = BottomBarMode.Navigation }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test fun pagerSwipeMovesTheLensBeforeThePageChanges() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ReleaseBar() } }
        fun centerX(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center.x
        val start = centerX("nav-models")
        val end = centerX("nav-benchmarks")
        val pager = compose.onNodeWithTag("navigation-test-pager")
        pager.performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.3f, 0f), delayMillis = 120)
        }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        assertTrue(centerX("nav-indicator") in (start + 2f)..(end - 2f))
        pager.performTouchInput { up() }
    }

    @Test fun lensSamplesThemedLabelsAndLivePagePixels() {
        var pageColor by mutableStateOf(Color(0xFF1C9A55))
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFA91646))) {
                ReleaseBar(pageColor = pageColor)
            }
        }
        val capsule = compose.onNodeWithTag("glass-navigation-capsule")
        val bounds = capsule.fetchSemanticsNode().boundsInRoot
        val lens = compose.onNodeWithTag("nav-indicator").fetchSemanticsNode().boundsInRoot
        val tabLeft = (lens.left - bounds.left).roundToInt()
        val tabRight = (lens.right - bounds.left).roundToInt()
        val y = (lens.center.y - bounds.top).roundToInt()
        val x = tabLeft + (lens.width * 0.25f).roundToInt()

        val green = capsule.captureToImage().toPixelMap()
        var tintedPixels = 0
        for (row in 0 until green.height) {
            for (column in tabLeft until tabRight) {
                val pixel = green[column, row]
                if (pixel.red - pixel.green > 0.22f && pixel.red - pixel.blue > 0.15f) tintedPixels++
            }
        }
        assertTrue("透镜应采到主题着色后的图标和文字", tintedPixels >= 12)
        compose.runOnIdle { pageColor = Color(0xFF1C559A) }
        val blue = capsule.captureToImage().toPixelMap()
        assertTrue("同一透镜应即时采到页面颜色变化", green[x, y].green - blue[x, y].green > 0.10f)
        assertTrue(blue[x, y].blue - green[x, y].blue > 0.10f)
    }

    private fun assertReleaseNavigation(largeRtl: Boolean) {
        var minimumPixels = 0f
        var opens = 0
        compose.setContent {
            val density = LocalDensity.current
            minimumPixels = with(density) { 48.dp.toPx() }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (largeRtl) 2f else 1f),
                LocalLayoutDirection provides if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ModelAtlasTheme(darkTheme = largeRtl, dynamicColor = false) {
                    ReleaseBar(onOpenChat = { opens++ })
                }
            }
        }
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
        val capsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(ai.width, ai.height, 1f)
        // 胶囊在起始侧，AI 圆形入口在末侧。
        assertTrue(if (largeRtl) ai.right < capsule.left else ai.left > capsule.right)
        for (tag in listOf("nav-chat", "nav-models", "nav-benchmarks", "nav-compare", "nav-settings")) {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width < 48dp", bounds.width >= minimumPixels - 1f)
            assertTrue("$tag height < 48dp", bounds.height >= minimumPixels - 1f)
        }
        compose.onNodeWithTag("nav-debug").assertDoesNotExist()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-chat").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-indicator").assertIsDisplayed()
        saveRegressionScreenshot("navigation-release-${if (largeRtl) "large-dark-rtl" else "light"}-ai-button")
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        val indicator = compose.onNodeWithTag("nav-indicator").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("nav-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(settings.center.x, indicator.center.x, 1f)
        saveRegressionScreenshot("navigation-release-${if (largeRtl) "large-dark-rtl" else "light"}-settings")
    }

    @Test fun navigationBarMorphsIntoComposerWithoutMovingCapsuleOrButton() {
        var mode by mutableStateOf(BottomBarMode.Navigation)
        var chat by mutableStateOf(ChatUiState(modelId = "model"))
        var sends = 0
        var stops = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ReleaseBar(
                    mode = mode,
                    chatState = chat,
                    onOpenChat = { mode = BottomBarMode.Chat },
                    onChatInput = { chat = chat.copy(input = it) },
                    onSend = { sends++ },
                    onStop = { stops++ },
                )
            }
        }
        val navCapsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot

        compose.runOnIdle { mode = BottomBarMode.Chat }
        compose.waitForIdle()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").assertDoesNotExist()
        compose.onNodeWithTag("nav-indicator").assertDoesNotExist()
        val chatCapsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        // 水平位置与宽度保持连续，仅高度随内容变化。
        assertEquals(navCapsule.left, chatCapsule.left, 1f)
        assertEquals(navCapsule.right, chatCapsule.right, 1f)
        assertEquals(navCapsule.height, chatCapsule.height, 1f)
        val disabledSend = compose.onNodeWithTag("chat-send").assertIsDisplayed().assertIsNotEnabled()
        val sendBounds = disabledSend.fetchSemanticsNode().boundsInRoot
        assertEquals(ai.left, sendBounds.left, 1f)
        assertEquals(ai.right, sendBounds.right, 1f)
        assertEquals(ai.top, sendBounds.top, 1f)
        assertEquals(ai.bottom, sendBounds.bottom, 1f)

        compose.onNodeWithTag("chat-input").performTextInput("问题")
        compose.onNodeWithTag("chat-send").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, sends) }

        compose.runOnIdle { chat = chat.copy(isGenerating = true) }
        compose.onNodeWithTag("chat-stop").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("chat-stop").performClick()
        compose.runOnIdle { assertEquals(1, stops) }

        compose.runOnIdle { chat = chat.copy(isGenerating = false) }
        compose.onNodeWithTag("chat-send").assertIsDisplayed()
        saveRegressionScreenshot("navigation-morph-chat")

        compose.runOnIdle { mode = BottomBarMode.Navigation }
        compose.waitForIdle()
        compose.onNodeWithTag("nav-chat").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertDoesNotExist()
        val restored = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(navCapsule.left, restored.left, 1f)
        assertEquals(navCapsule.right, restored.right, 1f)
    }

    @Test fun multilineDraftGrowsCapsuleAndStopsAtTheFieldLimit() {
        var mode by mutableStateOf(BottomBarMode.Chat)
        var chat by mutableStateOf(ChatUiState(modelId = "model"))
        var density = 0f
        compose.setContent {
            density = LocalDensity.current.density
            ModelAtlasTheme(dynamicColor = false) {
                ReleaseBar(
                    mode = mode,
                    chatState = chat,
                    onChatInput = { chat = chat.copy(input = it) },
                )
            }
        }
        val single = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("chat-input").performTextInput("第一行\n第二行\n第三行")
        val multiline = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertTrue("多行输入应自然增高胶囊", multiline.height > single.height + 1f)
        compose.onNodeWithTag("chat-input").performTextInput("\n" + "更多内容\n".repeat(12))
        val capped = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        // 测试窗口 240dp 高：内容上限 72dp + 上下 12dp 内边距。
        assertTrue("长文本必须限制高度并在编辑器内部滚动", capped.height <= 96f * density + 4f)
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        compose.runOnIdle { mode = BottomBarMode.Navigation }
        compose.waitForIdle()
        compose.onNodeWithTag("nav-chat").assertIsDisplayed()
    }

    @Composable
    private fun ReleaseBar(
        mode: BottomBarMode = BottomBarMode.Navigation,
        chatState: ChatUiState = ChatUiState(),
        pageColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
        onNavigate: suspend (Int) -> Unit = {},
        onOpenChat: () -> Unit = {},
        onChatInput: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onStop: () -> Unit = {},
    ) {
        val items = listOf(GlassNavigationItem.Models, GlassNavigationItem.Benchmarks, GlassNavigationItem.Compare, GlassNavigationItem.Settings)
        val pager = rememberPagerState(pageCount = { items.size })
        val backdrop = rememberLayerBackdrop { drawContent() }
        val latestChat = rememberUpdatedState(chatState)
        Box(Modifier.requiredSize(320.dp, 240.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 16.dp)) {
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("navigation-test-pager").layerBackdrop(backdrop)) {
                Box(Modifier.fillMaxSize().background(pageColor))
            }
            MorphingBottomBar(
                mode = mode,
                pagerState = pager,
                items = items,
                backdrop = backdrop,
                chatState = latestChat,
                onNavigate = {
                    onNavigate(it)
                    pager.animateScrollToPage(it)
                },
                onModelsDoubleClick = {},
                onOpenChat = onOpenChat,
                onChatInput = onChatInput,
                onSend = onSend,
                onStop = onStop,
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
            )
        }
    }
}
