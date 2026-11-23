package io.github.chenlongapps.modelatlas.ui.chat

import android.content.ClipData
import android.content.ClipDescription
import android.content.res.Configuration
import android.os.PersistableBundle
import android.view.WindowInsets as PlatformWindowInsets
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
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
import com.kyant.backdrop.Backdrop
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.components.glass.GlassStyle
import io.github.chenlongapps.modelatlas.ui.components.glass.captureGlassBackdrop
import io.github.chenlongapps.modelatlas.ui.components.glass.glassSurface
import io.github.chenlongapps.modelatlas.ui.components.glass.rememberGlassBackdrop
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

private val chatSuggestions = listOf(
    R.string.chat_suggestion_context,
    R.string.chat_suggestion_price,
    R.string.chat_suggestion_weights,
    R.string.chat_suggestion_moe,
    R.string.chat_suggestion_cache,
    R.string.chat_suggestion_multimodal,
    R.string.chat_suggestion_benchmarks,
    R.string.chat_suggestion_latency,
    R.string.chat_suggestion_quantization,
    R.string.chat_suggestion_reasoning,
    R.string.chat_suggestion_tools,
    R.string.chat_suggestion_structured_output,
)
private val chatTopActionSize = 48.dp
private val chatTopActionGap = 8.dp
private val chatTopFadeTail = 24.dp

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun ChatScreen(
    uiState: ChatUiState,
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
    isVisible: Boolean = true,
    onBack: (() -> Unit)? = null,
    onSendSuggestion: (String) -> Unit = {},
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
    // 在列表项外抽样，避免滚动、配置面板开合或输入引发的重组换题。
    val suggestions = remember { chatSuggestions.shuffled().take(3) }
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
    val view = LocalView.current
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
    val title = stringResource(R.string.chat_title)
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val openConfig = { focus.clearFocus(); keyboard?.hide(); onOpenConfig() }
    val navigateBack: (() -> Unit)? = onBack?.let { back ->
        {
            // 点击可能早于 IME inset 引起的重组，按此时的窗口状态决定是否退页。
            if (view.rootWindowInsets?.isVisible(PlatformWindowInsets.Type.ime()) ?: imeVisible) {
                focus.clearFocus()
                keyboard?.hide()
            } else back()
        }
    }
    val imeBottom = WindowInsets.ime.only(WindowInsetsSides.Bottom)
    val safeDrawing = WindowInsets.safeDrawing
    val safeHorizontal = safeDrawing.only(WindowInsetsSides.Horizontal)
    val safeTop = safeDrawing.asPaddingValues().calculateTopPadding()
    val topFadeHeight = safeTop + chatTopActionGap + chatTopActionSize + chatTopFadeTail
    val bottomFadeHeight = safeDrawing.exclude(imeBottom).asPaddingValues().calculateBottomPadding() + bottomOverlayPadding
    val backdrop = rememberGlassBackdrop(background)
    Surface(
        modifier = modifier.fillMaxSize()
            .semantics { paneTitle = title }
            .testTag("chat-screen"),
        color = background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // 只有键盘收缩视口。系统栏和浮层的避让放进列表首尾，让消息滚到渐变后面。
        Box(Modifier.fillMaxSize().windowInsetsPadding(imeBottom)) {
            Box(Modifier.matchParentSize().captureGlassBackdrop(backdrop).chatEdgeFades(background, topFadeHeight, bottomFadeHeight)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(safeHorizontal)
                        .widthIn(max = 720.dp).fillMaxSize()
                        .nestedScroll(scrollConnection).testTag("chat-messages"),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = topFadeHeight + 8.dp, bottom = bottomFadeHeight + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "session-status", contentType = "session-status") { ChatConversationStatus(uiState) }
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
                    if (uiState.turns.isEmpty()) item(key = "welcome") {
                        ChatWelcome(suggestions, isVisible && uiState.canSendSuggestion) { text ->
                            focus.clearFocus()
                            keyboard?.hide()
                            onSendSuggestion(text)
                        }
                    }
                }
            }
            ChatFloatingActions(
                uiState, isVisible, openConfig, navigateBack, backdrop,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(safeHorizontal)
                    .padding(start = 16.dp, end = 16.dp, top = safeTop + chatTopActionGap),
            )
        }
    }
    uiState.configDraft?.takeIf { isVisible }?.let { draft ->
        ChatConfigSheet(draft, onEditConfig, onApplyConfig, onDismissConfig,
            loading = uiState.configLoading, saving = uiState.configSaving, loadFailed = uiState.configLoadFailed,
            failure = uiState.configFailure, confirmClear = uiState.confirmClearConfig,
            startsNewConversation = draft.changed && uiState.turns.isNotEmpty(),
            onReload = onReloadConfig, onRequestClear = onRequestClearConfig,
            onCancelClear = onCancelClearConfig, onClear = onClearConfig)
    }
}

private fun Modifier.chatEdgeFades(color: Color, topHeight: Dp, bottomHeight: Dp) = drawWithCache {
    val preferredTop = topHeight.toPx()
    val preferredBottom = bottomHeight.toPx()
    // 横屏或键盘打开时也留一段未遮罩的正文，不让两端渐变叠加。
    val scale = ((size.height - 48.dp.toPx()).coerceAtLeast(0f) / (preferredTop + preferredBottom)).coerceAtMost(1f)
    val top = preferredTop * scale
    val bottom = preferredBottom * scale
    val topFade = if (top > 0f) Brush.verticalGradient(
        0f to color.copy(alpha = 0.96f),
        0.54f to color.copy(alpha = 0.74f),
        1f to color.copy(alpha = 0f),
        endY = top,
    ) else null
    val bottomFade = if (bottom > 0f) Brush.verticalGradient(
        0f to color.copy(alpha = 0f),
        0.46f to color.copy(alpha = 0.74f),
        1f to color.copy(alpha = 0.96f),
        startY = size.height - bottom,
        endY = size.height,
    ) else null
    onDrawWithContent {
        drawContent()
        topFade?.let { drawRect(it, size = Size(size.width, top)) }
        bottomFade?.let {
            drawRect(it, topLeft = Offset(0f, size.height - bottom), size = Size(size.width, bottom))
        }
    }
}

@Composable
private fun ChatWelcome(suggestions: List<Int>, enabled: Boolean, onSend: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.chat_welcome), style = MaterialTheme.typography.titleLarge)
        for (suggestion in suggestions) {
            val text = stringResource(suggestion)
            Surface(
                onClick = { onSend(text) }, enabled = enabled,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().testTag("chat-suggestion"),
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
private fun ChatConversationStatus(uiState: ChatUiState) {
    Text(
        if (uiState.configLoading) stringResource(R.string.chat_config_loading) else uiState.modelId ?: stringResource(R.string.chat_not_configured),
        modifier = Modifier.testTag("chat-session-status"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChatFloatingActions(
    uiState: ChatUiState,
    enabled: Boolean,
    onOpenConfig: () -> Unit,
    onBack: (() -> Unit)?,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.fillMaxWidth()) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                enabled = enabled,
                modifier = Modifier.align(Alignment.CenterStart).size(chatTopActionSize)
                    .glassSurface(backdrop, CircleShape, GlassStyle.Subtle, tint = surface, fallbackColor = surface)
                    .testTag("chat-back"),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
            }
        }
        IconButton(
            onClick = onOpenConfig,
            enabled = enabled && !uiState.isGenerating && !uiState.configLoading && !uiState.configSaving,
            modifier = Modifier.align(Alignment.CenterEnd).size(width = 64.dp, height = chatTopActionSize)
                .glassSurface(backdrop, CircleShape, GlassStyle.Subtle, tint = surface, fallbackColor = surface)
                .testTag("chat-config"),
        ) {
            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.chat_config_title))
        }
    }
}

@Composable
private fun ChatTurnContent(turn: ChatTurn, canRetry: Boolean, onRetry: () -> Unit) {
    val reply by turn.reply.collectAsStateWithLifecycle()
    val showRetry = canRetry && (reply.status == ChatReplyStatus.FAILED || reply.status == ChatReplyStatus.STOPPED)
    Column(Modifier.fillMaxWidth().testTag("chat-turn-${turn.id}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        UserMessage(turn)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.chat_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reply.status == ChatReplyStatus.WAITING) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.chat_waiting), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (reply.text.isNotEmpty()) {
                AssistantMarkdown(reply, modifier = Modifier.testTag("chat-reply-${turn.id}"))
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
            if (showRetry) {
                Text(stringResource(R.string.chat_retry_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (reply.text.isNotEmpty() || showRetry) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (reply.text.isNotEmpty()) {
                        CopyMessageButton(reply.text, modifier = Modifier.testTag("chat-copy-reply-${turn.id}"))
                    }
                    if (showRetry) {
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("chat-retry")) {
                            Text(stringResource(R.string.chat_retry))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessage(turn: ChatTurn) {
    val userLabel = stringResource(R.string.chat_user)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val bubbleMaxWidth = maxWidth * 0.85f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Bottom) {
            CopyMessageButton(turn.user, modifier = Modifier.testTag("chat-copy-user-${turn.id}"))
            // 权重不填满，让短消息贴合文字；复制入口留在气泡外，也不抢选择手势。
            Surface(
                modifier = Modifier.weight(1f, fill = false).widthIn(max = bubbleMaxWidth)
                    .testTag("chat-user-${turn.id}").semantics { contentDescription = userLabel },
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                SelectionContainer(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(turn.user, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** 跟随列表实测高度，覆盖异步 Markdown 排版，且不使历史列表或输入区逐 token 重组。
 * 正向列表使阅读同一条长回复的中部也保持原文字锚点（反向整项布局会随增长移动文字）。 */
@Composable
private fun ChatFollowLatest(turn: ChatTurn?, listState: LazyListState, follow: Boolean) {
    val shouldFollow by rememberUpdatedState(follow)
    LaunchedEffect(listState, turn) {
        if (turn == null) return@LaunchedEffect
        var lastViewport = listState.layoutInfo.viewportSize to listState.layoutInfo.afterContentPadding
        if (shouldFollow) listState.scrollToChatBottom()
        snapshotFlow {
            val layout = listState.layoutInfo
            val viewport = layout.viewportSize to layout.afterContentPadding
            viewport to layout.visibleItemsInfo.lastOrNull { it.key == turn.id }?.size
        }.collect { (viewport, height) ->
            val resized = viewport != lastViewport
            lastViewport = viewport
            if ((resized || height != null) && shouldFollow) listState.scrollToChatBottom()
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
internal fun CopyMessageButton(text: String, modifier: Modifier = Modifier, @StringRes label: Int = R.string.chat_copy) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    IconButton(modifier = modifier.size(48.dp), onClick = {
        scope.launch {
            val clip = ClipData.newPlainText("", text)
            clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            clipboard.setClipEntry(ClipEntry(clip))
        }
    }) {
        Icon(
            painterResource(R.drawable.ic_content_copy), stringResource(label),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Chat · Light", showBackground = true)
@Preview(name = "Chat · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Chat · Large font", widthDp = 320, fontScale = 2f)
@Preview(name = "Chat · Landscape", widthDp = 740, heightDp = 360)
@Composable
private fun ChatPreview() {
    ModelAtlasTheme(dynamicColor = false) { ChatScreen(ChatUiState(), bottomOverlayPadding = 96.dp, onBack = {}) }
}
