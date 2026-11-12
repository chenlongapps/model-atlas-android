package io.github.chenlongapps.modelatlas.ui.chat

import android.content.ClipData
import android.content.ClipDescription
import android.content.res.Configuration
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ChatScreen(
    uiState: ChatUiState,
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
    isVisible: Boolean = true,
    onBack: (() -> Unit)? = null,
    onInput: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onOpenConfig: () -> Unit = {},
    onEditConfig: (String, String, String) -> Unit = { _, _, _ -> },
    onApplyConfig: () -> Unit = {},
    onDismissConfig: () -> Unit = {},
    onReloadConfig: () -> Unit = {},
    onRequestClearConfig: () -> Unit = {},
    onCancelClearConfig: () -> Unit = {},
    onClearConfig: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    var followLatest by rememberSaveable { mutableStateOf(true) }
    val scrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && consumed.y > 0f) followLatest = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.canScrollForward, listState.lastScrolledBackward) }.collect { (scrolling, canScroll, backwards) ->
            if (scrolling && !canScroll) followLatest = true
            else if (scrolling && backwards) followLatest = false
        }
    }
    if (isVisible) ChatFollowLatest(uiState.turns.lastOrNull(), listState, followLatest)
    // 发送动作位于共享底栏；出现新轮次时恢复跟随并滚到末尾。
    var knownTurnCount by rememberSaveable { mutableIntStateOf(uiState.turns.size) }
    LaunchedEffect(uiState.turns.size) {
        if (uiState.turns.size > knownTurnCount) {
            followLatest = true
            withFrameNanos { }
            listState.scrollToChatBottom()
        }
        knownTurnCount = uiState.turns.size
    }
    // IME 可见性不能由 bottom > 0 推断：浮动键盘或已处理 inset 的宿主可能高度为零。
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    if (!LocalInspectionMode.current) {
        val backOwner = rememberNavigationEventDispatcherOwner(enabled = isVisible)
        CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides backOwner) {
            NavigationBackHandler(
                state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
                isBackEnabled = imeVisible && uiState.configDraft == null,
                onBackCompleted = { focus.clearFocus(); keyboard?.hide() },
            )
        }
    }
    Scaffold(
        modifier = modifier.testTag("chat-screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        // 一次性消费系统栏、刘海及 IME；共享输入栏位于页面之外，不在此叠加 imePadding。
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val compact = maxHeight < 400.dp
            val openConfig = { focus.clearFocus(); keyboard?.hide(); onOpenConfig() }
            val navigateBack: (() -> Unit)? = onBack?.let { back ->
                { if (imeVisible) { focus.clearFocus(); keyboard?.hide() } else back() }
            }
            // 共享输入栏由外壳绘制在页面之上，消息区始终为它保留高度；IME 由 Scaffold 一次消费。
            Column(Modifier.fillMaxSize().padding(bottom = bottomOverlayPadding)) {
                if (compact) {
                    // 短窗口把返回与配置入口固定在顶部，标题仍随消息滚动。
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        navigateBack?.let { back -> ChatBackButton(isVisible, back) }
                        Spacer(Modifier.weight(1f))
                        ChatConfigButton(uiState, isVisible, openConfig)
                    }
                } else {
                    ChatHeader(uiState, isVisible, openConfig, onBack = navigateBack)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(scrollConnection).testTag("chat-messages"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // 短窗口标题参与滚动；消息仍按稳定轮次 key 保留阅读锚点。
                    if (compact) item(key = "header", contentType = "header") {
                        ChatHeader(uiState, isVisible, openConfig, showConfig = false)
                    }
                    if (uiState.modelId == null || (uiState.configLoadFailed && uiState.configDraft == null)) item(key = "configuration", contentType = "configuration") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.configLoadFailed && uiState.configDraft == null) {
                                uiState.configFailure?.let { ChatConfigFailure(it) }
                                TextButton(onClick = onReloadConfig, enabled = isVisible && !uiState.configLoading) {
                                    Text(stringResource(R.string.chat_config_reload))
                                }
                            }
                            if (uiState.modelId == null) {
                                TextButton(onClick = openConfig, enabled = isVisible && !uiState.configLoading && !uiState.configSaving) {
                                    Text(stringResource(R.string.chat_config_required))
                                }
                            }
                        }
                    }
                    items(uiState.turns, key = { it.id }, contentType = { "turn" }) { turn ->
                        ChatTurnContent(turn, canRetry = turn === uiState.turns.lastOrNull() && !uiState.isGenerating && uiState.configurationReady, onRetry)
                    }
                    if (uiState.turns.isEmpty()) item(key = "welcome") { ChatWelcome(isVisible, onInput) }
                }
            }
        }
    }
    uiState.configDraft?.takeIf { isVisible }?.let { draft ->
        ChatConfigDialog(draft, onEditConfig, onApplyConfig, onDismissConfig,
            loading = uiState.configLoading, saving = uiState.configSaving, loadFailed = uiState.configLoadFailed,
            failure = uiState.configFailure, confirmClear = uiState.confirmClearConfig,
            onReload = onReloadConfig, onRequestClear = onRequestClearConfig,
            onCancelClear = onCancelClearConfig, onClear = onClearConfig)
    }
}

@Composable
private fun ChatWelcome(enabled: Boolean, onInput: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.chat_welcome), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.chat_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (suggestion in listOf(R.string.chat_suggestion_context, R.string.chat_suggestion_price, R.string.chat_suggestion_weights)) {
            val text = stringResource(suggestion)
            Surface(
                onClick = { onInput(text) }, enabled = enabled,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(uiState: ChatUiState, isVisible: Boolean, onOpenConfig: () -> Unit, showConfig: Boolean = true, onBack: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        onBack?.let { ChatBackButton(isVisible, it) }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                if (uiState.configLoading) stringResource(R.string.chat_config_loading) else uiState.modelId ?: stringResource(R.string.chat_not_configured),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showConfig) ChatConfigButton(uiState, isVisible, onOpenConfig)
    }
}

@Composable
private fun ChatBackButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag("chat-back")) {
        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
    }
}

@Composable
private fun ChatConfigButton(uiState: ChatUiState, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled && !uiState.isGenerating && !uiState.configLoading && !uiState.configSaving, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.chat_config_title))
    }
}

@Composable
private fun ChatTurnContent(turn: ChatTurn, canRetry: Boolean, onRetry: () -> Unit) {
    val reply by turn.reply.collectAsStateWithLifecycle()
    val userLabel = stringResource(R.string.chat_user)
    Column(Modifier.fillMaxWidth().testTag("chat-turn-${turn.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.End).semantics { contentDescription = userLabel },
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SelectionContainer { Text(turn.user, style = MaterialTheme.typography.bodyLarge) }
                CopyMessageButton(turn.user)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (reply.status == ChatReplyStatus.WAITING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.chat_waiting), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (reply.text.isNotEmpty()) {
                SelectionContainer { Text(reply.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("chat-reply-${turn.id}")) }
                CopyMessageButton(reply.text)
            }
            when (reply.status) {
                ChatReplyStatus.FAILED -> {
                    val message = when (reply.error) {
                        ChatError.NETWORK -> R.string.chat_error_network
                        ChatError.TIMEOUT -> R.string.chat_error_timeout
                        ChatError.HTTP -> R.string.chat_error_http
                        ChatError.PROTOCOL -> R.string.chat_error_protocol
                        ChatError.REMOTE -> R.string.chat_error_remote
                        else -> R.string.chat_error_unknown
                    }
                    Text(stringResource(message, reply.httpStatus ?: 0), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                ChatReplyStatus.STOPPED -> Text(stringResource(R.string.chat_stopped), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChatReplyStatus.COMPLETE -> if (reply.text.isEmpty()) Text(stringResource(R.string.chat_empty_response), style = MaterialTheme.typography.bodyMedium)
                else -> Unit
            }
            if (canRetry && reply.status in setOf(ChatReplyStatus.FAILED, ChatReplyStatus.STOPPED)) {
                Text(stringResource(R.string.chat_retry_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("chat-retry")) { Text(stringResource(R.string.chat_retry)) }
            }
        }
    }
}

/** 独立收集当前回复，不使历史列表或输入区逐 token 重组；至多每帧跟随一次。
 * 正向列表使阅读同一条长回复的中部也保持原文字锚点（反向整项布局会随增长移动文字）。 */
@Composable
private fun ChatFollowLatest(turn: ChatTurn?, listState: LazyListState, follow: Boolean) {
    val shouldFollow by rememberUpdatedState(follow)
    LaunchedEffect(turn, listState) {
        turn?.reply?.collect {
            withFrameNanos { }
            if (shouldFollow) listState.scrollToChatBottom()
        }
    }
    LaunchedEffect(listState, turn) {
        snapshotFlow { listState.layoutInfo.viewportSize }.collect {
            if (turn != null && shouldFollow) listState.scrollToChatBottom()
        }
    }
}

private suspend fun LazyListState.scrollToChatBottom() {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == last }) scrollToItem(last)
    // offset 使用实测整项高度，LazyColumn 会约束在真实内容尾部，不限制长回复的长度。
    val height = layoutInfo.visibleItemsInfo.lastOrNull { it.index == last }?.size ?: 0
    scrollToItem(last, height)
}

@Composable
private fun CopyMessageButton(text: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    TextButton(onClick = {
        scope.launch {
            val clip = ClipData.newPlainText("", text)
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            clipboard.setClipEntry(ClipEntry(clip))
        }
    }) { Text(stringResource(R.string.chat_copy)) }
}

@Preview(name = "Chat · Light", showBackground = true)
@Preview(name = "Chat · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Chat · Large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Chat · Landscape", widthDp = 740, heightDp = 360)
@Composable
private fun ChatPreview() {
    ModelAtlasTheme(dynamicColor = false) { ChatScreen(ChatUiState(), bottomOverlayPadding = 96.dp) }
}
