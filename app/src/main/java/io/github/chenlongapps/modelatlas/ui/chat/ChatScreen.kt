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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ChatScreen(
    uiState: ChatUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onInput: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onStop: () -> Unit = {},
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
    val scope = rememberCoroutineScope()
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
    ChatFollowLatest(uiState.turns.lastOrNull(), listState, followLatest)
    // IME 可见性不能由 bottom > 0 推断：浮动键盘或已处理 inset 的宿主可能高度为零。
    val imeVisible = WindowInsets.isImeVisible
    val leave = { focus.clearFocus(); keyboard?.hide(); onBack() }
    if (!LocalInspectionMode.current) {
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            isBackEnabled = uiState.configDraft == null,
            onBackCompleted = {
                if (imeVisible) { focus.clearFocus(); keyboard?.hide() } else leave()
            },
        )
    }
    Scaffold(
        modifier = modifier.testTag("chat-screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        // 一次性消费系统栏、刘海及 IME；底部编辑器不再叠加 imePadding。
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = leave) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    Text(
                        if (uiState.configLoading) stringResource(R.string.chat_config_loading) else uiState.modelId ?: stringResource(R.string.chat_not_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { focus.clearFocus(); keyboard?.hide(); onOpenConfig() }, enabled = !uiState.isGenerating && !uiState.configLoading && !uiState.configSaving) {
                    Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.chat_config_title))
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(scrollConnection).testTag("chat-messages"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(uiState.turns, key = { it.id }, contentType = { "turn" }) { turn ->
                    ChatTurnContent(turn, canRetry = turn === uiState.turns.lastOrNull() && !uiState.isGenerating && uiState.configurationReady, onRetry)
                }
                if (uiState.turns.isEmpty()) item(key = "welcome") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.chat_welcome), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.chat_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        for (suggestion in listOf(R.string.chat_suggestion_context, R.string.chat_suggestion_price, R.string.chat_suggestion_weights)) {
                            val text = stringResource(suggestion)
                            TextButton(onClick = { onInput(text) }) { Text(text) }
                        }
                    }
                }
            }
            if (uiState.configLoadFailed && uiState.configDraft == null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    uiState.configFailure?.let { ChatConfigFailure(it) }
                    TextButton(onClick = onReloadConfig, enabled = !uiState.configLoading) { Text(stringResource(R.string.chat_config_reload)) }
                }
            }
            if (uiState.modelId == null) {
                TextButton(onClick = onOpenConfig, enabled = !uiState.configLoading && !uiState.configSaving, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.chat_config_required))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // 横屏／大字号时留出消息空间；输入仍可在内部滚动，无固定行高。
                val minInputHeight = 56.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
                val inputMaxHeight = (maxHeight * 0.3f).coerceIn(minInputHeight, maxOf(minInputHeight, 180.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = uiState.input,
                        onValueChange = onInput,
                        modifier = Modifier.weight(1f).heightIn(max = inputMaxHeight).testTag("chat-input"),
                        label = { Text(stringResource(R.string.chat_input)) },
                        minLines = 1,
                        maxLines = 6,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )
                    if (uiState.isGenerating) FilledTonalButton(onClick = onStop, modifier = Modifier.testTag("chat-stop")) {
                        Text(stringResource(R.string.chat_stop))
                    } else FilledTonalButton(
                        enabled = uiState.canSend,
                        onClick = {
                            followLatest = true
                            onSend()
                            scope.launch { withFrameNanos { }; listState.scrollToChatBottom() }
                        },
                        modifier = Modifier.testTag("chat-send"),
                    ) { Text(stringResource(R.string.chat_send)) }
                }
            }
        }
    }
    uiState.configDraft?.let { draft ->
        ChatConfigDialog(draft, onEditConfig, onApplyConfig, onDismissConfig,
            loading = uiState.configLoading, saving = uiState.configSaving, loadFailed = uiState.configLoadFailed,
            failure = uiState.configFailure, confirmClear = uiState.confirmClearConfig,
            onReload = onReloadConfig, onRequestClear = onRequestClearConfig,
            onCancelClear = onCancelClearConfig, onClear = onClearConfig)
    }
}

@Composable
private fun ChatTurnContent(turn: ChatTurn, canRetry: Boolean, onRetry: () -> Unit) {
    val reply by turn.reply.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().testTag("chat-turn-${turn.id}"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.align(Alignment.End),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.chat_user), style = MaterialTheme.typography.labelMedium)
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
    ModelAtlasTheme(dynamicColor = false) { ChatScreen(ChatUiState(), onBack = {}) }
}
