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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Debug 测试宿主显式传 Release 的四项胶囊，避免只验证带调试条目的布局。 */
class GlassNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun releaseNavigationAt320DpKeepsCircleAndFourTabsUsable() = assertReleaseNavigation(false)

    @Test fun releaseNavigationAt320DpLargeFontDarkRtlKeepsCircleAndFourTabsUsable() = assertReleaseNavigation(true)

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
        onOpenChat: () -> Unit = {},
        onChatInput: (String) -> Unit = {},
        onSend: () -> Unit = {},
        onStop: () -> Unit = {},
    ) {
        val items = listOf(GlassNavigationItem.Models, GlassNavigationItem.Benchmarks, GlassNavigationItem.Compare, GlassNavigationItem.Settings)
        val pager = rememberPagerState(pageCount = { items.size })
        val scope = rememberCoroutineScope()
        val backdrop = rememberLayerBackdrop { drawContent() }
        val latestChat = rememberUpdatedState(chatState)
        Box(Modifier.requiredSize(320.dp, 240.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 16.dp)) {
            HorizontalPager(pager, Modifier.fillMaxSize().layerBackdrop(backdrop)) { }
            MorphingBottomBar(
                mode = mode,
                pagerState = pager,
                items = items,
                backdrop = backdrop,
                chatState = latestChat,
                onNavigate = { scope.launch { pager.animateScrollToPage(it) } },
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
