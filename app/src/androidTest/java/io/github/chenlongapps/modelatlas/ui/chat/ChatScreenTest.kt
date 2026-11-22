package io.github.chenlongapps.modelatlas.ui.chat

import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.ai.LlmChunk
import io.github.chenlongapps.modelatlas.ai.LlmClient
import io.github.chenlongapps.modelatlas.ai.LlmConfig
import io.github.chenlongapps.modelatlas.ai.LlmException
import io.github.chenlongapps.modelatlas.ai.LlmMessage
import io.github.chenlongapps.modelatlas.ai.LlmRequest
import io.github.chenlongapps.modelatlas.ai.LlmResponse
import io.github.chenlongapps.modelatlas.ai.LlmRole
import io.github.chenlongapps.modelatlas.ai.config.LlmConfigRepository
import io.github.chenlongapps.modelatlas.ai.config.ConfigStorageException
import io.github.chenlongapps.modelatlas.ai.config.ConfigFailure
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private val fake = FakeClient()
    private val configs = MemoryConfigs()
    private lateinit var vm: ChatViewModel
    private lateinit var view: View
    private lateinit var lifecycle: LifecycleRegistry
    private var exits = 0

    @After fun close() { InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() } }

    @Test fun missingConfigDisablesSuggestionsAndSavingDoesNotSend() {
        show(configured = false)
        compose.onNodeWithText("请先配置 API 和模型").assertIsDisplayed()
        compose.onNodeWithText("消息将发送至你配置的 API。", substring = true).assertDoesNotExist()
        val suggestions = compose.onAllNodesWithTag("chat-suggestion")
        suggestions.assertCountEquals(3).assertAll(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        compose.runOnIdle { configure(); vm.setInput(" \n ") }
        compose.onNodeWithTag("chat-send").assertIsNotEnabled()
        assertEquals(0, fake.calls.get())
    }

    @Test fun suggestionsStayStableAndTapSendsOnlyThatQuestionWithoutChangingDraft() {
        show()
        val suggestions = compose.onAllNodesWithTag("chat-suggestion")
        fun questions() = suggestions.fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].single().text }

        val shown = questions()
        assertEquals(3, shown.size)
        assertEquals(3, shown.toSet().size)
        compose.onNodeWithContentDescription("会话配置").performClick()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.runOnIdle { vm.setInput("手写草稿") }
        assertEquals(shown, questions())
        suggestions[0].performClick()
        compose.waitUntil { fake.calls.get() == 1 }
        compose.onNode(hasText(shown.first()) and hasAnyAncestor(hasTestTag("chat-user-1"))).assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assert(hasText("手写草稿"))
        suggestions.assertCountEquals(0)
        assertEquals(listOf(LlmMessage(LlmRole.USER, shown.first())), fake.lastRequest.messages)
    }

    @Test fun streamingAppendsOneMessageAndStopRetainsItThenRetryDoesNotDuplicateUser() {
        show(); send()
        compose.onNodeWithText("正在等待回复…").assertIsDisplayed()
        fake.channel.trySend(LlmChunk("第一段\n"))
        fake.channel.trySend(LlmChunk("第二段"))
        awaitReply(1, "第一段\n第二段").assertIsDisplayed()
        compose.onAllNodesWithTag("chat-turn-1").assertCountEquals(1)
        compose.onNodeWithTag("chat-stop").performClick()
        compose.waitUntil { fake.active.get() == 0 }
        compose.onNodeWithText("已停止生成。").assertIsDisplayed()
        awaitReply(1, "第一段\n第二段").assertIsDisplayed()
        compose.onNodeWithTag("chat-retry").performClick()
        compose.waitUntil { fake.calls.get() == 2 }
        compose.runOnIdle { assertEquals(1, vm.uiState.value.turns.size) }
        fake.channel.trySend(LlmChunk("完整回答")); fake.channel.close()
        awaitReply(1, "完整回答").assertIsDisplayed()
        compose.onNodeWithText("第一段\n第二段").assertDoesNotExist()
    }

    @Test fun failureKeepsPartialTextAndDisplaysExplicitPaidRetry() {
        show(); send()
        fake.channel.trySend(LlmChunk("部分文本"))
        fake.channel.close(LlmException.Protocol())
        awaitReply(1, "部分文本").assertIsDisplayed()
        compose.onNodeWithText("响应格式错误或传输中断，回复未完成。").assertIsDisplayed()
        compose.onNodeWithText("重新生成会替换本轮回复，可能再次计费。").assertIsDisplayed()
        compose.onNodeWithTag("chat-retry").assertIsDisplayed()
        assertEquals(1, fake.calls.get())
    }

    @Test fun configurationSheetValidatesAndAppliesWithoutNetwork() {
        show(configured = false)
        compose.onNodeWithContentDescription("会话配置").performClick()
        compose.onNodeWithTag("chat-config-apply").performClick()
        compose.onNodeWithText("配置无效：", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("chat-base-url").performTextInput("https://example.test/custom/api")
        compose.onNodeWithTag("chat-api-key").performTextInput("test-key")
        compose.onNodeWithTag("chat-model-id").performTextInput("test-model")
        compose.onNodeWithTag("chat-config-apply").performClick()
        compose.onNodeWithText("test-model").assertIsDisplayed()
        assertEquals(0, fake.calls.get())
    }

    @Test fun configurationHelpAndKeyVisibilityAreOnDemandInASecureWindow() {
        show()
        compose.onNodeWithContentDescription("会话配置").performClick()
        compose.onNodeWithText("配置仅保存在本机", substring = true).assertDoesNotExist()
        compose.onNodeWithText("连接助手使用的模型服务。").assertDoesNotExist()
        compose.onNodeWithText("清除已保存配置").assertDoesNotExist()
        compose.onNodeWithText("重新配置").assertDoesNotExist()
        val sheet = sheetView()
        compose.runOnIdle {
            val window = (sheet.parent as DialogWindowProvider).window
            assertTrue(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        val hint = compose.onNodeWithText("填写完整的 HTTPS API 根路径", substring = true)
        hint.assertDoesNotExist()
        compose.onNodeWithContentDescription("显示 API Base URL 填写说明").performClick()
        hint.performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("隐藏 API Base URL 填写说明").performClick()
        hint.assertDoesNotExist()

        val key = compose.onNodeWithTag("chat-api-key")
        val masked = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        val obfuscated = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("•".repeat(8)))
        key.assert(masked and obfuscated)
        compose.onNodeWithContentDescription("显示 API Key").performClick()
        key.assert(masked and SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("test-key")))
        compose.onNodeWithContentDescription("隐藏 API Key").performClick()
        key.assert(masked and obfuscated)
        compose.onNodeWithContentDescription("显示 API Key").performClick()
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithContentDescription("会话配置").performClick()
        key.assert(masked and obfuscated)
    }

    @Test fun keyboardLeavesApplyVisibleAndSystemBackDismissesKeyboardBeforeSheet() {
        show()
        compose.onNodeWithContentDescription("会话配置").performClick()
        val sheet = sheetView()
        compose.onNodeWithTag("chat-model-id").performScrollTo().performClick()
        compose.waitUntil(5_000) { ViewCompat.getRootWindowInsets(sheet)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
        val apply = compose.onNodeWithTag("chat-config-apply").assertIsDisplayed().fetchSemanticsNode().boundsInWindow
        val imeTop = sheet.rootView.height - ViewCompat.getRootWindowInsets(sheet)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue("应用按钮应完整位于键盘上方", apply.bottom <= imeTop + 1f)
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { ViewCompat.getRootWindowInsets(sheet)?.isVisible(WindowInsetsCompat.Type.ime()) != true }
        compose.onNodeWithTag("chat-config-apply").assertIsDisplayed()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { vm.uiState.value.configDraft == null }
        compose.onNodeWithTag("chat-config-apply").assertDoesNotExist()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
    }

    @Test fun longModelIdEllipsizesAndUserBubblesKeepCopyOutside() {
        val model = "provider/" + "long-model-id-".repeat(10)
        val short = ChatTurn(1, "你好")
        val long = ChatTurn(2, "长消息会自然换行。".repeat(5))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                Box(Modifier.size(320.dp, 480.dp)) {
                    ChatScreen(ChatUiState(modelId = model, turns = listOf(short, long)), onBack = {})
                }
            }
        }
        val modelLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(model).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(modelLayout) }
        assertEquals(1, modelLayout.single().lineCount)
        assertTrue(modelLayout.single().isLineEllipsized(0))
        compose.onNodeWithTag("chat-back").assertIsDisplayed()
        compose.onNodeWithContentDescription("会话配置").assertIsDisplayed()

        val turn = compose.onNodeWithTag("chat-turn-2").fetchSemanticsNode().boundsInRoot
        val shortBubble = compose.onNodeWithTag("chat-user-1").fetchSemanticsNode().boundsInRoot
        val longBubble = compose.onNodeWithTag("chat-user-2").fetchSemanticsNode().boundsInRoot
        val copy = compose.onNodeWithTag("chat-copy-user-2").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(shortBubble.width < longBubble.width)
        assertTrue(longBubble.width <= turn.width * 0.85f + 1f)
        assertEquals(turn.right, longBubble.right, 1f)
        assertTrue(copy.right <= longBubble.left)
    }

    @Test fun unreadableConfigRequiresConfirmationBeforeOpeningAnEditableForm() {
        configs.config = LlmConfig("https://example.test/api", "test-key", "unreadable")
        configs.loadError = ConfigStorageException(ConfigFailure.KEY_UNAVAILABLE)
        show(configured = false)
        compose.onNodeWithContentDescription("会话配置").performClick()
        compose.onNodeWithText("重新配置").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertNotNull(configs.config) }
        compose.onNodeWithText("重新配置").performScrollTo().performClick()
        compose.onNodeWithText("删除并继续").performClick()
        compose.onNodeWithTag("chat-config-sheet").assertIsDisplayed()
        compose.onNodeWithTag("chat-base-url").assertIsEnabled()
        compose.onNodeWithText("重新配置").assertDoesNotExist()
        compose.runOnIdle { assertNull(configs.config); assertEquals(0, fake.calls.get()) }
    }

    @Test fun configurationButtonShowsNewConversationOnlyForChangedConfigWithTurns() {
        show(); send()
        fake.channel.close()
        compose.waitUntil { !vm.uiState.value.isGenerating }
        compose.onNodeWithContentDescription("会话配置").performClick()
        val apply = compose.onNodeWithTag("chat-config-apply")
        apply.assert(hasText("应用"))
        compose.onNodeWithText("更改配置会清空当前对话。").assertDoesNotExist()
        compose.onNodeWithTag("chat-model-id").performTextInput("-new")
        apply.assert(hasText("应用并开始新对话")).performClick()
        compose.onNodeWithText("想了解模型的哪些方面？").assertIsDisplayed()
        assertEquals(1, fake.calls.get())
    }

    @Test fun loadFailureCanRetryButCannotOverwriteCredentials() {
        configs.loadError = ConfigStorageException(ConfigFailure.KEY_UNAVAILABLE)
        show(configured = false)
        compose.onNodeWithText("无法加密或解密 API Key。", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("会话配置").performClick()
        compose.onNodeWithTag("chat-config-apply").assertIsNotEnabled()
        compose.onNodeWithTag("chat-base-url").assertIsNotEnabled()
        configs.loadError = null
        configs.config = LlmConfig("https://example.test/api", "test-key", "recovered")
        compose.onNodeWithText("重试读取").performScrollTo().performClick()
        compose.onNodeWithTag("chat-model-id").assert(hasText("recovered"))
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithText("recovered").assertIsDisplayed()
        assertEquals(0, fake.calls.get())
    }

    @Test fun savingFailureRetainsDraftAndOldConfigUntilExplicitRetry() {
        show()
        configs.saveError = ConfigStorageException(ConfigFailure.STORAGE)
        val saveGate = CompletableDeferred<Unit>()
        configs.saveGate = saveGate
        compose.onNodeWithContentDescription("会话配置").performClick()
        compose.onNodeWithTag("chat-model-id").performTextInput("-new")
        compose.onNodeWithTag("chat-config-apply").performClick()
        compose.onNodeWithTag("chat-config-apply").assertIsNotEnabled()
        compose.onNodeWithContentDescription("关闭").assertIsNotEnabled()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("chat-config-apply").assertIsDisplayed()
        saveGate.complete(Unit)
        compose.onNodeWithText("配置读取或保存失败。", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals("model", vm.uiState.value.modelId); assertNotNull(vm.uiState.value.configDraft) }
        configs.saveError = null
        compose.onNodeWithTag("chat-config-apply").performClick()
        compose.runOnIdle { assertNull(vm.uiState.value.configDraft); assertEquals(configs.config!!.modelId, vm.uiState.value.modelId) }
        assertEquals(0, fake.calls.get())
    }

    @Test fun newViewModelRestoresOnlyAppliedConfigWithoutMessagesOrAutomaticRequests() {
        init()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            vm.setInput("private draft")
            vm.openConfig(); vm.editConfig("https://discarded.test", "discarded-key", "discarded-model"); vm.dismissConfig()
        }
        show(configured = false)
        compose.onNodeWithText("model").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals("", vm.uiState.value.input)
            assertTrue(vm.uiState.value.turns.isEmpty())
            assertEquals(0, fake.calls.get())
        }
    }

    @Test fun configurationErrorAndClearConfirmationRemainReachableAtNarrowLargeFontRtl() {
        var state by mutableStateOf(ChatUiState(
            configDraft = ChatConfigDraft(), configLoadFailed = true, configFailure = ConfigFailure.KEY_UNAVAILABLE,
        ))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(dynamicColor = false) {
                    Box(Modifier.requiredSize(320.dp, 640.dp)) {
                        ChatScreen(state, onRequestClearConfig = { state = state.copy(confirmClearConfig = true) },
                            onCancelClearConfig = { state = state.copy(confirmClearConfig = false) })
                    }
                }
            }
        }
        compose.onNodeWithTag("chat-config-apply").assertIsNotEnabled()
        compose.onNodeWithText("无法加密或解密 API Key。", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重试读取").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重新配置").performScrollTo().performClick()
        compose.onNodeWithText("删除并继续").assertIsDisplayed()
        // 弹窗窗口持有焦点且前序用例可能仍显示 IME；直接注入系统返回，避免 Espresso 等待基础窗口焦点。
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("chat-config-apply").assertIsNotEnabled()
    }

    @Test fun credentialsInputAndMessagesAreAbsentFromSavedUiState() {
        val registry = SaveableStateRegistry(restoredValues = null) { true }
        val turn = ChatTurn(1, "private-user-text").also { it.update(ChatReply("private-reply-text", ChatReplyStatus.COMPLETE)) }
        val state = ChatUiState(
            turns = listOf(turn), input = "private-input-text",
            configDraft = ChatConfigDraft("https://example.test/api", "never-save-secret", "model"),
        )
        compose.setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                ModelAtlasTheme(dynamicColor = false) {
                    ModelAtlasScaffold(
                        chatUiState = rememberUpdatedState(state),
                        chatContent = { padding, visible, onBack ->
                            ChatScreen(state, bottomOverlayPadding = padding, isVisible = visible, onBack = onBack)
                        },
                    ) { _, _, _ -> }
                }
            }
        }
        compose.onNodeWithTag("nav-chat").performClick()
        compose.runOnIdle {
            val saved = registry.performSave().toString()
            for (privateText in listOf("never-save-secret", "private-user-text", "private-reply-text", "private-input-text")) {
                assertFalse("敏感文字不能注册到 UI 保存状态", saved.contains(privateText))
            }
        }
    }

    @Test fun recompositionRestorationAndLifecycleRecollectionDoNotRestartGeneration() {
        val restoration = StateRestorationTester(compose)
        var redraw by mutableStateOf(false)
        init()
        restoration.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                ModelAtlasTheme(darkTheme = redraw, dynamicColor = false) {
                    val state = vm.uiState.collectAsStateWithLifecycle()
                    ModelAtlasScaffold(
                        chatUiState = state,
                        onChatInput = vm::setInput,
                        onChatSend = vm::send,
                        onChatStop = vm::stop,
                        chatContent = { padding, visible, onBack ->
                            ChatRoute(bottomOverlayPadding = padding, isVisible = visible, onBack = onBack, viewModel = vm)
                        },
                    ) { _, _, _ -> }
                }
            }
        }
        compose.onNodeWithTag("nav-chat").performClick()
        send()
        fake.channel.trySend(LlmChunk("还在生成"))
        compose.runOnIdle { redraw = true }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.CREATED }
        compose.runOnIdle { lifecycle.currentState = Lifecycle.State.RESUMED }
        awaitReply(1, "还在生成").assertIsDisplayed()
        assertEquals(1, fake.calls.get())
        assertEquals(1, fake.active.get())
        fake.channel.close()
    }

    @Test fun incomingTextKeepsBottomAnchorButDoesNotPullReaderFromOlderMessages() {
        val turns = List(20) { index -> ChatTurn(index.toLong(), "问题 $index").also {
            it.update(ChatReply("回答 $index\n".repeat(6), ChatReplyStatus.COMPLETE))
        } }
        lateinit var list: LazyListState
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                list = rememberLazyListState()
                ChatScreen(ChatUiState(turns = turns, modelId = "model"), listState = list)
            }
        }
        val first = "追加\n".repeat(30).trimEnd()
        compose.runOnIdle { turns.last().update(ChatReply(first, ChatReplyStatus.STREAMING)) }
        awaitReply(19, first)
        compose.waitUntil(5_000) { !list.canScrollForward }
        compose.onNodeWithTag("chat-messages").performScrollToIndex(10)
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        var index = 0
        var offset = 0
        compose.runOnIdle { index = list.firstVisibleItemIndex; offset = list.firstVisibleItemScrollOffset }
        compose.runOnIdle { turns.last().update(ChatReply(first + "\n" + "追加更多\n".repeat(80), ChatReplyStatus.STREAMING)) }
        compose.runOnIdle { assertEquals(index, list.firstVisibleItemIndex); assertEquals(offset, list.firstVisibleItemScrollOffset) }
    }

    @Test fun readingInsideGrowingLongResponseKeepsTheSameTextPosition() {
        val turn = ChatTurn(1, "问题").also { it.update(ChatReply("已有内容\n".repeat(150), ChatReplyStatus.STREAMING)) }
        lateinit var list: LazyListState
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                list = rememberLazyListState()
                ChatScreen(ChatUiState(turns = listOf(turn), modelId = "model", isGenerating = true), listState = list)
            }
        }
        awaitReply(1, "已有内容", substring = true)
        compose.waitUntil(5_000) { !list.canScrollForward && list.firstVisibleItemScrollOffset > 0 }
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        var before = 0
        compose.runOnIdle { before = list.firstVisibleItemScrollOffset; assertTrue(list.canScrollForward) }
        compose.runOnIdle { turn.update(ChatReply("已有内容\n".repeat(150) + "新增内容\n".repeat(100), ChatReplyStatus.STREAMING)) }
        awaitReply(1, "新增内容", substring = true)
        compose.runOnIdle { assertEquals(before, list.firstVisibleItemScrollOffset) }
    }

    @Test fun restoringInsideLongMarkdownDoesNotLoseTheReadingOffsetWhileParsing() {
        val restoration = StateRestorationTester(compose)
        val text = List(100) { "## 第 $it 节\n\n有用的 **Markdown** 内容" }.joinToString("\n\n")
        val turn = ChatTurn(1, "问题").also { it.update(ChatReply(text, ChatReplyStatus.COMPLETE)) }
        lateinit var list: LazyListState
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                list = rememberLazyListState()
                ChatScreen(ChatUiState(turns = listOf(turn), modelId = "model"), listState = list)
            }
        }
        awaitReply(1, "第 99 节")
        compose.waitUntil(5_000) { !list.canScrollForward && list.firstVisibleItemScrollOffset > 0 }
        compose.onNodeWithTag("chat-messages").performTouchInput { swipeDown() }
        val before = compose.runOnIdle {
            assertTrue(list.canScrollForward)
            list.firstVisibleItemScrollOffset
        }
        restoration.emulateSavedInstanceStateRestore()
        awaitReply(1, "第 99 节")
        compose.runOnIdle { assertEquals(before, list.firstVisibleItemScrollOffset) }
    }

    @Test fun copyActionsCopyTheUserTextRawMarkdownAndCodeBody() {
        show(); send()
        val code = "  fun compass() = \"**代码）**🧭后文\""
        val text = "**完整回答）**后续\n包含换行和 Unicode 🧭\n\n```kotlin\n$code\n```"
        fake.channel.trySend(LlmChunk(text)); fake.channel.close()
        awaitReply(1, code)
        for ((button, expected) in listOf(
            compose.onNodeWithTag("chat-copy-user-1") to "问题\n第二行",
            compose.onNodeWithContentDescription("复制代码") to code,
            compose.onNodeWithTag("chat-copy-reply-1") to text,
        )) {
            button.performScrollTo().performClick()
            compose.runOnIdle {
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip!!
                assertEquals(expected, clip.getItemAt(0).text.toString())
                assertTrue(clip.description.extras!!.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE))
            }
        }
    }

    @Test fun narrowLargeFontRtlAndLandscapeKeepLongTextAndControlsReachable() {
        val turn = ChatTurn(1, "长消息\n".repeat(30)).also { it.update(ChatReply("模型完整回答\n".repeat(100), ChatReplyStatus.COMPLETE)) }
        val chat = ChatUiState(turns = listOf(turn), modelId = "model", input = "多行\n输入")
        var landscape by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                ModelAtlasTheme(darkTheme = landscape, dynamicColor = false) {
                    // size 遵循宿主真实宽度；requiredSize(640dp) 会把 RTL 发送按钮放到手机屏幕外。
                    Box(Modifier.size(if (landscape) 640.dp else 320.dp, if (landscape) 320.dp else 600.dp)) {
                        ModelAtlasScaffold(
                            chatUiState = rememberUpdatedState(chat),
                            chatContent = { padding, visible, onBack ->
                                ChatScreen(chat, bottomOverlayPadding = padding, isVisible = visible, onBack = onBack)
                            },
                        ) { _, _, _ -> }
                    }
                }
            }
        }
        compose.onNodeWithTag("nav-chat").performClick()
        compose.onNodeWithTag("chat-send").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        awaitReply(1, "模型完整回答", substring = true)
        compose.onNodeWithTag("chat-messages").performScrollToNode(hasText("模型完整回答", substring = true))
        compose.onNodeWithTag("chat-copy-reply-1").assertExists()
        compose.runOnIdle { landscape = true }
        compose.onNodeWithTag("chat-send").assertIsDisplayed()
        compose.onNodeWithTag("chat-input").assertIsDisplayed()
        val screen = compose.onNodeWithTag("chat-screen").fetchSemanticsNode().boundsInRoot
        for (tag in listOf("chat-send", "chat-input")) {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag must be fully inside the short window", bounds.left >= screen.left && bounds.right <= screen.right && bounds.bottom <= screen.bottom)
        }
    }

    @Test fun visibleKeyboardConsumesFirstSystemBackAndNextBackFallsThroughWithoutLosingDraft() {
        show()
        compose.onNodeWithTag("chat-input").performTextInput("保留草稿\n第二行")
        compose.onNodeWithTag("chat-input").performClick()
        compose.waitUntil(5_000) { imeVisible() }
        val imeTop = view.height - ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val bottom = compose.onNodeWithTag("chat-input").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("编辑器应在 IME 上方", bottom <= imeTop + 1f)
        pressBack()
        compose.waitUntil(5_000) { !imeVisible() }
        compose.runOnIdle { assertEquals(0, exits); assertEquals("保留草稿\n第二行", vm.uiState.value.input) }
        pressBack()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun leavingTabStopsUnderlyingRequestAndReentryDoesNotRestartIt() {
        init()
        var visible by mutableStateOf(true)
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) { ChatRoute(isVisible = visible, viewModel = vm) }
        }
        // 输入栏已迁到共享底栏；此用例只验证离屏停止与重入状态，直接驱动 ViewModel。
        compose.runOnIdle { vm.setInput("问题\n第二行"); vm.send() }
        compose.waitUntil { fake.calls.get() == 1 }
        fake.channel.trySend(LlmChunk("保留部分回复"))
        awaitReply(1, "保留部分回复").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.waitUntil { fake.active.get() == 0 }
        compose.runOnIdle { assertEquals(ChatReplyStatus.STOPPED, vm.uiState.value.turns.single().reply.value.status); visible = true }
        awaitReply(1, "保留部分回复").assertIsDisplayed()
        assertEquals(1, fake.calls.get())
    }

    @Test fun offscreenConfigurationDoesNotOpenAWindowAndDraftReturnsOnReentry() {
        init()
        var visible by mutableStateOf(false)
        compose.runOnIdle { vm.openConfig() }
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) { ChatRoute(isVisible = visible, viewModel = vm) }
        }
        compose.onNodeWithTag("chat-config-apply").assertDoesNotExist()
        compose.runOnIdle { visible = true }
        compose.onNodeWithTag("chat-config-apply").assertIsDisplayed()
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("chat-config-apply").assertDoesNotExist()
    }

    private lateinit var owner: LifecycleOwner
    private fun init(configured: Boolean = true) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
            lifecycle = owner.lifecycle as LifecycleRegistry
            lifecycle.currentState = Lifecycle.State.RESUMED
            vm = ChatViewModel(fake, configs)
            store.put("chat", vm)
            if (configured) configure()
        }
    }
    private fun show(configured: Boolean = true) {
        init(configured)
        compose.setContent {
            view = LocalView.current
            val state = vm.uiState.collectAsStateWithLifecycle()
            ModelAtlasTheme(dynamicColor = false) {
                // 输入栏由共享底栏提供，页面本身不再包含第二套编辑器。
                ModelAtlasScaffold(
                    chatUiState = state,
                    onChatInput = vm::setInput,
                    onChatSend = vm::send,
                    onChatStop = vm::stop,
                    onChatClosed = { exits++; vm.stop() },
                    chatContent = { padding, visible, onBack ->
                        ChatRoute(bottomOverlayPadding = padding, isVisible = visible, onBack = onBack, viewModel = vm)
                    },
                ) { _, _, _ -> }
            }
        }
        compose.onNodeWithTag("nav-chat").performClick()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
    }
    private fun configure() {
        vm.openConfig(); vm.editConfig("https://example.test/api", "test-key", "model"); vm.applyConfig()
    }
    private fun send() {
        compose.onNodeWithTag("chat-input").performTextInput("问题\n第二行")
        compose.onNodeWithTag("chat-send").performClick()
        compose.waitUntil { fake.calls.get() == 1 }
    }
    private fun awaitReply(id: Long, text: String, substring: Boolean = false): androidx.compose.ui.test.SemanticsNodeInteraction {
        val matcher = hasText(text, substring = substring) and hasAnyAncestor(hasTestTag("chat-reply-$id"))
        compose.waitUntil(5_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1 }
        return compose.onNode(matcher)
    }
    private fun imeVisible() = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
    private fun sheetView(): View =
        (compose.onNodeWithTag("chat-config-sheet").fetchSemanticsNode().root as ViewRootForTest).view

    private class MemoryConfigs : LlmConfigRepository {
        var config: LlmConfig? = null
        var loadError: Exception? = null
        var saveError: Exception? = null
        var saveGate: CompletableDeferred<Unit>? = null
        override suspend fun load(): LlmConfig? { loadError?.let { throw it }; return config }
        override suspend fun save(config: LlmConfig) { saveGate?.await(); saveError?.let { throw it }; this.config = config }
        override suspend fun clear() { config = null }
    }

    private class FakeClient : LlmClient {
        val calls = AtomicInteger()
        val active = AtomicInteger()
        lateinit var lastRequest: LlmRequest
        lateinit var channel: Channel<LlmChunk>
        override suspend fun complete(config: LlmConfig, request: LlmRequest): LlmResponse = error("stream only")
        override fun stream(config: LlmConfig, request: LlmRequest) = flow {
            lastRequest = request
            val input = Channel<LlmChunk>(Channel.UNLIMITED)
            channel = input
            calls.incrementAndGet(); active.incrementAndGet()
            try { for (delta in input) emit(delta) } finally { active.decrementAndGet(); input.cancel() }
        }
    }
}
