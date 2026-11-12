package io.github.chenlongapps.modelatlas.ui.chat

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import io.github.chenlongapps.modelatlas.ai.LlmChunk
import io.github.chenlongapps.modelatlas.ai.LlmClient
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmResponse
import io.github.chenlongapps.modelatlas.ai.config.LlmConfigRepository
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.testing.saveRegressionScreenshot
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelBackTestInput
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow

class ChatNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var view: View
    private val store = ViewModelStore()

    @After fun close() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }

    @Test fun independentAiCircleOpensFullScreenRouteAndBackRestoresSource() {
        compose.setContent { Content() }
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
        val capsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals("AI 入口应保持圆形", ai.width, ai.height, 1f)
        assertTrue("AI 应位于胶囊右侧并留出间隔", ai.left > capsule.right)
        compose.onNode(hasTestTag("nav-chat") and hasAnyAncestor(hasTestTag("glass-navigation-capsule"))).assertDoesNotExist()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithContentDescription("AI 助手").performClick()
        assertFullScreen()
        compose.onNodeWithTag("chat-back").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("nav-indicator").assertIsDisplayed()
    }

    @Test fun assistantRouteKeepsBottomBarGeometryWhileMorphingAndRestoring() {
        compose.setContent { Content() }
        val navCapsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("nav-chat").performClick()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("发送").assertIsDisplayed()
        val chatCapsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        val send = compose.onNodeWithTag("chat-send").fetchSemanticsNode().boundsInRoot
        // 胶囊与右侧入口在路由切换前后保持同一位置和形状，仅内容形态变化。
        assertEquals(navCapsule.left, chatCapsule.left, 1f)
        assertEquals(navCapsule.right, chatCapsule.right, 1f)
        assertEquals(navCapsule.top, chatCapsule.top, 1f)
        assertEquals(navCapsule.bottom, chatCapsule.bottom, 1f)
        assertEquals(ai.left, send.left, 1f)
        assertEquals(ai.right, send.right, 1f)
        compose.onNodeWithTag("chat-back").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithContentDescription("AI 助手").assertIsDisplayed()
        val restoredCapsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(navCapsule.left, restoredCapsule.left, 1f)
        assertEquals(navCapsule.right, restoredCapsule.right, 1f)
        assertEquals(navCapsule.top, restoredCapsule.top, 1f)
        assertEquals(navCapsule.bottom, restoredCapsule.bottom, 1f)
    }

    @Test fun assistantIsIndependentAndSettingsHasNoDuplicateEntry() {
        compose.setContent { Content() }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        open()
        assertFullScreen()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        assertControlsInsideScreen()
        saveRegressionScreenshot("chat-route-light")
        pressBack()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-chat").assertDoesNotExist()
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithTag("data-source-screen").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.onNodeWithTag("appearance-screen").assertIsDisplayed()
    }

    @Test fun tabSwitchPreservesCatalogPositionAndOtherTabsStillWork() {
        compose.setContent { Content() }
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("模型 12"))
        compose.onNodeWithTag("model-list").performTouchInput { swipe(center, center + Offset(0f, 100f), 400) }
        val before = compose.onNodeWithText("模型 12").fetchSemanticsNode().boundsInRoot.top
        open()
        compose.onNodeWithTag("chat-back").performClick()
        compose.onNodeWithTag("nav-compare").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-benchmarks").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun restoredAssistantRouteKeepsNavigationHiddenAndReturnsToRestoredSource() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.onNodeWithTag("nav-settings").performClick()
        open()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
        assertFullScreen()
        pressBack()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }

    @Test fun firstBackHidesKeyboardAndSecondBackPopsRouteWithoutLosingDraft() {
        compose.setContent { Content() }
        open()
        compose.onNodeWithTag("chat-input").performTextInput("保留草稿")
        compose.waitUntil(5_000) { imeVisible() }
        assertFullScreen()
        saveRegressionScreenshot("chat-route-keyboard")
        val imeTop = view.height - ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue(compose.onNodeWithTag("chat-input").fetchSemanticsNode().boundsInRoot.bottom <= imeTop + 1f)
        pressBack()
        compose.waitUntil(5_000) { !imeVisible() }
        assertFullScreen()
        compose.onNodeWithText("保留草稿").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        open()
        compose.onNodeWithText("保留草稿").assertIsDisplayed()
        assertFalse("重入不能自动唤起键盘", imeVisible())
    }

    @Test fun offscreenAssistantDoesNotConsumeSettingsSubpageBack() {
        compose.setContent { Content() }
        open()
        pressBack()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("数据来源与许可").performClick()
        pressBack()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
    }

    @Test fun toolbarBackHidesKeyboardBeforePoppingFullScreenRoute() {
        compose.setContent { Content() }
        compose.onNodeWithTag("nav-settings").performClick()
        open()
        compose.onNodeWithTag("chat-input").performTextInput("返回按钮草稿")
        compose.waitUntil(5_000) { imeVisible() }
        compose.onNodeWithTag("chat-back").performClick()
        compose.waitUntil(5_000) { !imeVisible() }
        assertFullScreen()
        compose.onNodeWithTag("chat-back").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        open()
        compose.onNodeWithText("返回按钮草稿").assertIsDisplayed()
    }

    @Test fun visibilitySignalOnlyEnablesOpenAssistantRoute() {
        var visible = false
        compose.setContent { Content { visible = it } }
        compose.runOnIdle { assertFalse(visible) }
        open()
        compose.runOnIdle { assertTrue(visible) }
        compose.onNodeWithTag("chat-back").performClick()
        compose.runOnIdle { assertFalse(visible) }
        open()
        compose.runOnIdle { assertTrue(visible) }
    }

    @Test fun readingPositionAndConversationSurvivePopAndReentry() {
        lateinit var list: LazyListState
        val turns = List(24) { i -> ChatTurn(i.toLong(), "问题 $i").also {
            it.update(ChatReply("回答 $i\n".repeat(8), ChatReplyStatus.COMPLETE))
        } }
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(chatContent = { padding, visible, onBack ->
                    list = rememberLazyListState()
                    ChatScreen(ChatUiState(modelId = "model", turns = turns), bottomOverlayPadding = padding, isVisible = visible, onBack = onBack, listState = list)
                }) { _, _, _ -> }
            }
        }
        open()
        compose.onNodeWithTag("chat-messages").performScrollToIndex(10)
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        var before = 0 to 0
        compose.runOnIdle { before = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        compose.onNodeWithTag("chat-back").performClick()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        open()
        compose.runOnIdle { assertEquals(before, list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset) }
        assertFullScreen()
    }

    @Test fun confirmedPredictiveReturnReenablesSourceNavigationScrollTracking() {
        val input = ModelBackTestInput()
        compose.setContent {
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.removeInput(input) }
            }
            Content(navigationAutoHideEnabled = true)
        }
        open()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { input.start(); input.progress(.6f) }
        // 让来源 Main 在助手仍为当前路由时预组合，再确认返回。
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { input.complete() }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("model-list").performTouchInput {
            swipe(center, center - Offset(0f, 200f), 600)
        }
        compose.onNodeWithTag("nav-chat").assertDoesNotExist()
        compose.onNodeWithTag("nav-models").assertDoesNotExist()
        compose.onNodeWithTag("model-list").performTouchInput {
            swipe(center, center + Offset(0f, 200f), 600)
        }
        compose.onNodeWithTag("nav-chat").assertIsDisplayed()
        compose.onNodeWithTag("nav-models").assertIsSelected()
    }

    @Test fun poppedAssistantImmediatelyDisablesExitContentBeforeAnimationFinishes() {
        var clicks = 0
        lateinit var closeRoute: () -> Unit
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(chatContent = { _, _, onBack ->
                    closeRoute = onBack
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { clicks++ }, modifier = Modifier.testTag("route-exit-action")) { Text("测试操作") }
                    }
                }) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-chat").performClick()
        val action = compose.onNodeWithTag("route-exit-action").fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { closeRoute() }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("route-exit-action").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(action) }
        compose.runOnIdle { assertEquals(0, clicks) }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("nav-models").assertIsSelected()
    }

    @Test fun rootRouteRestorationAndPredictiveCancelKeepRequestButConfirmedPopStopsIt() {
        val client = StreamingClient()
        val input = ModelBackTestInput()
        lateinit var vm: ChatViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm = ChatViewModel(client, MemoryConfig())
            store.put("chat", vm)
        }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            view = LocalView.current
            val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
            DisposableEffect(dispatcher) {
                dispatcher.addInput(input)
                onDispose { dispatcher.removeInput(input) }
            }
            ModelAtlasTheme(dynamicColor = false) {
                val chatState = vm.uiState.collectAsStateWithLifecycle()
                ModelAtlasScaffold(
                    chatUiState = chatState,
                    onChatInput = vm::setInput,
                    onChatSend = vm::send,
                    onChatStop = vm::stop,
                    onChatClosed = vm::stop,
                    chatContent = { padding, visible, onBack ->
                        ChatRoute(bottomOverlayPadding = padding, isVisible = visible, onBack = onBack, viewModel = vm)
                    },
                ) { _, _, _ -> }
            }
        }
        open()
        compose.waitUntil { vm.uiState.value.configurationReady }
        compose.onNodeWithTag("chat-input").performTextInput("仅内存问题")
        // 等 IME 与消息区布局稳定后再发送，避免断言和键盘动画竞争。
        compose.waitUntil(5_000) { imeVisible() }
        compose.onNodeWithTag("chat-send").performClick()
        compose.waitUntil { client.active.get() == 1 }
        client.channel.trySend(LlmChunk("保留部分回复"))
        compose.waitUntil { vm.uiState.value.turns.single().reply.value.text == "保留部分回复" }
        compose.onNodeWithText("保留部分回复").assertIsDisplayed()
        compose.waitUntil(5_000) { imeVisible() }
        pressBack()
        compose.waitUntil(5_000) { !imeVisible() }
        restoration.emulateSavedInstanceStateRestore()
        assertFullScreen()
        compose.runOnIdle { assertEquals(1, client.active.get()); assertEquals(1, client.calls.get()) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { input.start(); input.progress(.55f) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { input.cancel() }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        assertFullScreen()
        compose.runOnIdle { assertEquals(1, client.active.get()); assertEquals(1, client.calls.get()) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            input.edge = androidx.navigationevent.NavigationEvent.EDGE_RIGHT
            input.start(); input.progress(1f); input.complete()
        }
        compose.mainClock.advanceTimeBy(700)
        compose.mainClock.autoAdvance = true
        compose.waitUntil { client.active.get() == 0 }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        open()
        compose.onNodeWithText("保留部分回复").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(ChatReplyStatus.STOPPED, vm.uiState.value.turns.single().reply.value.status)
            assertEquals(1, client.calls.get())
        }
    }

    @Test fun splitNavigationAt320DpLargeFontRtlKeepsTouchTargetsAndAssistantInputClear() {
        var minimumPixels = 0f
        compose.setContent {
            val density = LocalDensity.current
            minimumPixels = with(density) { 48.dp.toPx() }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                Box(Modifier.requiredSize(320.dp, 760.dp)) { Content() }
            }
        }
        for (tag in listOf("nav-models", "nav-benchmarks", "nav-compare", "nav-chat", "nav-settings", "nav-debug")) {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width < 48dp", bounds.width >= minimumPixels - 1f)
            assertTrue("$tag height < 48dp", bounds.height >= minimumPixels - 1f)
        }
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
        val capsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(ai.width, ai.height, 1f)
        assertTrue("RTL 的 AI 入口应位于末侧（左侧）", ai.right < capsule.left)
        open()
        assertFullScreen()
        assertControlsInsideScreen()
        compose.onNodeWithTag("chat-send").assertIsDisplayed()
        compose.onNodeWithContentDescription("会话配置").assertIsDisplayed()
        saveRegressionScreenshot("chat-route-320dp-large-rtl")
    }

    @Test fun shortWindowWithLargeFontKeepsReturnInputAndConfigurationReachable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.size(640.dp, 320.dp)) {
                        ModelAtlasScaffold(chatContent = { padding, visible, onBack ->
                            ChatScreen(ChatUiState(modelId = "model", input = "第一行\n第二行"), bottomOverlayPadding = padding, isVisible = visible, onBack = onBack)
                        }) { _, _, _ -> }
                    }
                }
            }
        }
        open()
        compose.onNodeWithTag("chat-send").assertIsDisplayed()
        assertFullScreen()
        assertControlsInsideScreen()
        compose.onNodeWithTag("chat-messages").performScrollToNode(hasText("AI 助手"))
        compose.onNodeWithContentDescription("会话配置").assertIsDisplayed()
        compose.onNodeWithTag("chat-back").assertIsDisplayed()
        saveRegressionScreenshot("chat-route-short-large-rtl")
    }

    @Test fun emptyAndNormalStatesRemainUsableInLightDarkAndDynamicThemes() {
        var theme by mutableStateOf(0)
        var chat by mutableStateOf(ChatUiState(modelId = "example-model"))
        compose.setContent {
            ModelAtlasTheme(darkTheme = theme == 1, dynamicColor = theme == 2) {
                ModelAtlasScaffold(
                    chatUiState = rememberUpdatedState(chat),
                    chatContent = { padding, visible, onBack ->
                        ChatScreen(chat, bottomOverlayPadding = padding, isVisible = visible, onBack = onBack)
                    },
                ) { _, _, _ -> }
            }
        }
        open()
        for (variant in 0..2) {
            compose.runOnIdle { theme = variant; chat = ChatUiState(modelId = "example-model") }
            compose.onNodeWithText("想了解模型的哪些方面？").assertIsDisplayed()
            compose.onNodeWithContentDescription("会话配置").assertIsDisplayed()
            saveRegressionScreenshot("chat-theme-$variant-empty")
            val turn = ChatTurn(1, "上下文长度和最大输出有什么区别？").also {
                it.update(ChatReply("上下文长度限制一次请求可处理的总量；最大输出限制单次回复的长度。具体限制请以服务商文档为准。", ChatReplyStatus.COMPLETE))
            }
            compose.runOnIdle { chat = chat.copy(turns = listOf(turn)) }
            compose.onNodeWithTag("chat-reply-1").assertIsDisplayed()
            compose.onNodeWithTag("chat-send").assertIsDisplayed()
            saveRegressionScreenshot("chat-theme-$variant-normal")
            if (variant == 1) {
                compose.runOnIdle { turn.update(ChatReply("保留的部分回复", ChatReplyStatus.FAILED, ChatError.NETWORK)) }
                compose.onNodeWithTag("chat-retry").assertIsDisplayed()
                saveRegressionScreenshot("chat-theme-dark-failure")
                compose.runOnIdle { turn.update(ChatReply()); chat = chat.copy(isGenerating = true) }
                compose.onNodeWithTag("chat-stop").assertIsDisplayed()
                compose.onNodeWithText("正在等待回复…").assertIsDisplayed()
                saveRegressionScreenshot("chat-theme-dark-waiting")
            }
        }
    }

    private fun open() {
        compose.onNodeWithTag("nav-chat").performClick()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
    }

    private fun assertFullScreen() {
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
        // 底栏保留并变形成输入栏，主导航内容与主页面不再可访问。
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        for (tag in listOf("main-pager", "nav-chat", "nav-models", "nav-settings")) {
            compose.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    private fun assertControlsInsideScreen() {
        val screen = compose.onNodeWithTag("chat-screen").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("chat-input", "chat-send", "chat-back")) {
            val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag 必须完整位于全屏助手内", bounds.left >= screen.left && bounds.right <= screen.right && bounds.bottom <= screen.bottom)
        }
    }

    private fun imeVisible() = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    private class MemoryConfig : LlmConfigRepository {
        override suspend fun load() = LlmConfig("https://example.test/api", "fake-route-key", "model")
        override suspend fun save(config: LlmConfig) = Unit
        override suspend fun clear() = Unit
    }

    private class StreamingClient : LlmClient {
        val calls = AtomicInteger()
        val active = AtomicInteger()
        val channel = Channel<LlmChunk>(Channel.UNLIMITED)
        override suspend fun complete(config: LlmConfig, request: LlmRequest): LlmResponse = error("stream only")
        override fun stream(config: LlmConfig, request: LlmRequest) = flow {
            calls.incrementAndGet(); active.incrementAndGet()
            try { for (chunk in channel) emit(chunk) } finally { active.decrementAndGet() }
        }
    }

    @Composable
    private fun Content(navigationAutoHideEnabled: Boolean = false, onVisibility: (Boolean) -> Unit = {}) {
        view = LocalView.current
        var chat by remember { mutableStateOf(ChatUiState()) }
        val latestChat = rememberUpdatedState(chat)
        val setInput: (String) -> Unit = { chat = chat.copy(input = it) }
        ModelAtlasTheme(dynamicColor = false) {
            ModelAtlasScaffold(
                navigationAutoHideEnabled = navigationAutoHideEnabled,
                chatUiState = latestChat,
                onChatInput = setInput,
                onChatClosed = { onVisibility(false) },
                chatContent = { padding, visible, onBack ->
                    LaunchedEffect(visible) { onVisibility(visible) }
                    ChatScreen(chat, bottomOverlayPadding = padding, isVisible = visible, onBack = onBack, onInput = setInput)
                },
            ) { padding, list, onClick ->
                ModelListScreen(ModelListUiState(models = List(30) { ModelSummary("lab/$it", "模型 $it") }, isLoading = false), bottomOverlayPadding = padding, listState = list, onModelClick = onClick)
            }
        }
    }
}
