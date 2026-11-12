package io.github.chenlongapps.modelatlas.ui.navigation

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.chat.ChatUiState

/** 底栏由当前导航路由决定形态，不维护独立的开关状态。 */
internal enum class BottomBarMode { Navigation, Chat }

/**
 * 主界面与助手共用的唯一底栏：胶囊玻璃表面保持不变，内部内容与末侧按钮随模式软化过渡。
 * 导航模式下胶囊在起始侧、AI 入口在末侧；聊天模式下胶囊成为输入栏、入口成为发送／停止。
 */
@Composable
internal fun MorphingBottomBar(
    mode: BottomBarMode,
    pagerState: PagerState,
    items: List<GlassNavigationItem>,
    backdrop: Backdrop,
    chatState: State<ChatUiState>,
    onNavigate: (Int) -> Unit,
    onModelsDoubleClick: () -> Unit,
    onOpenChat: () -> Unit,
    onChatInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    require(items.isNotEmpty())
    val spacing = if (items.size >= 5) 2.dp else 4.dp
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val modelsDoubleClick by rememberUpdatedState(onModelsDoubleClick)
    val colors = MaterialTheme.colorScheme
    val glassEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalInspectionMode.current
    val density = LocalDensity.current
    // 记住导航内容的实测高度，让输入胶囊在默认字号下与导航胶囊完全同高。
    var navigationContentHeight by remember { mutableStateOf(0.dp) }

    BoxWithConstraints(modifier.testTag("glass-navigation")) {
        val gap = 8.dp
        val capsuleMinWidth = 48.dp * items.size + 8.dp + spacing * (items.size - 1)
        val aiSize = (maxWidth - gap - capsuleMinWidth).coerceIn(48.dp, 64.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(mode, layoutDirection, items.size, spacing, aiSize, enabled) {
                    if (!enabled || mode != BottomBarMode.Navigation) return@pointerInput
                    detectModelsDoubleClicks({ width ->
                        navigationModelsRange(width, aiSize.toPx(), gap.toPx(), 4.dp.toPx(), spacing.toPx(), items.size, isRtl)
                    }) { modelsDoubleClick() }
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .navigationGlassSurface(backdrop, glassEnabled, colors.surfaceContainer, colors.background.copy(alpha = 0.5f))
                    .testTag("glass-navigation-capsule"),
            ) {
                AnimatedContent(
                    targetState = mode,
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    transitionSpec = {
                        (fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140)))
                            .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(durationMillis = 220) }))
                    },
                    label = "Bottom bar content",
                ) { target ->
                    // 退出端立即停用交互与读屏，动画期间只有目标形态可操作。
                    val interactive = enabled && mode == target
                    when (target) {
                        BottomBarMode.Navigation -> NavigationCapsuleContent(
                            pagerState = pagerState,
                            backdrop = backdrop,
                            onNavigate = onNavigate,
                            onModelsDoubleClick = onModelsDoubleClick,
                            items = items,
                            enabled = interactive,
                            modifier = Modifier
                                .interactionGate(interactive)
                                .onSizeChanged { navigationContentHeight = with(density) { it.height.toDp() } },
                        )
                        BottomBarMode.Chat -> ChatComposerContent(
                            chatState = chatState,
                            enabled = interactive,
                            onInput = onChatInput,
                            minHeight = (navigationContentHeight - 24.dp).coerceAtLeast(44.dp),
                            modifier = Modifier.interactionGate(interactive),
                        )
                    }
                }
            }
            AssistantActionButton(
                chatOpen = mode == BottomBarMode.Chat,
                chatState = chatState,
                size = aiSize,
                backdrop = backdrop,
                glassEnabled = glassEnabled,
                enabled = enabled,
                onOpenChat = onOpenChat,
                onSend = onSend,
                onStop = onStop,
            )
        }
    }
}

private fun Modifier.interactionGate(interactive: Boolean): Modifier =
    if (interactive) this else clearAndSetSemantics {}

@Composable
private fun ChatComposerContent(
    chatState: State<ChatUiState>,
    enabled: Boolean,
    onInput: (String) -> Unit,
    minHeight: Dp,
    modifier: Modifier = Modifier,
) {
    // 只订阅草稿文本；回复或生成状态变化不重组编辑器。
    val input by remember(chatState) { derivedStateOf { chatState.value.input } }
    val colors = MaterialTheme.colorScheme
    val placeholder = stringResource(R.string.chat_input)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 单行胶囊与导航胶囊同高；长文本有上限并在编辑器内部滚动。
        val maxFieldHeight = (maxHeight * 0.3f).coerceIn(44.dp, 140.dp).coerceAtLeast(minHeight)
        BasicTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .heightIn(min = minHeight, max = maxFieldHeight)
                .testTag("chat-input"),
            enabled = enabled,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            maxLines = 6,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (input.isEmpty()) {
                        Text(
                            placeholder,
                            style = textStyle.copy(color = colors.onSurfaceVariant),
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private enum class AssistantAction { Ai, Send, Stop }

@Composable
private fun AssistantActionButton(
    chatOpen: Boolean,
    chatState: State<ChatUiState>,
    size: Dp,
    backdrop: Backdrop,
    glassEnabled: Boolean,
    enabled: Boolean,
    onOpenChat: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    // 只在可用性与生成状态真正变化时重组，避免每个字符重建玻璃背景。
    val canSend by remember(chatState) { derivedStateOf { chatState.value.canSend } }
    val isGenerating by remember(chatState) { derivedStateOf { chatState.value.isGenerating } }
    val action = when {
        !chatOpen -> AssistantAction.Ai
        isGenerating -> AssistantAction.Stop
        else -> AssistantAction.Send
    }
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val label = stringResource(
        when (action) {
            AssistantAction.Ai -> R.string.chat_title
            AssistantAction.Send -> R.string.chat_send
            AssistantAction.Stop -> R.string.chat_stop
        },
    )
    val clickable = enabled && when (action) {
        AssistantAction.Ai -> true
        AssistantAction.Send -> canSend
        AssistantAction.Stop -> true
    }
    val tag = when (action) {
        AssistantAction.Ai -> "nav-chat"
        AssistantAction.Send -> "chat-send"
        AssistantAction.Stop -> "chat-stop"
    }
    Box(
        modifier = Modifier
            .size(size)
            .navigationGlassSurface(backdrop, glassEnabled, colors.surfaceContainer, colors.background.copy(alpha = 0.5f))
            .testTag(tag)
            .semantics { contentDescription = label }
            .clickable(
                enabled = clickable,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                when (action) {
                    AssistantAction.Ai -> onOpenChat()
                    AssistantAction.Send -> onSend()
                    AssistantAction.Stop -> onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 200)) + scaleIn(tween(durationMillis = 200), initialScale = 0.85f)) togetherWith
                    fadeOut(tween(durationMillis = 120))
            },
            modifier = Modifier.clearAndSetSemantics {},
            label = "Assistant action",
        ) { target ->
            when (target) {
                AssistantAction.Ai -> Text(
                    stringResource(R.string.nav_ai),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
                AssistantAction.Send -> Icon(
                    painterResource(R.drawable.ic_send),
                    contentDescription = null,
                    tint = if (canSend) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.38f),
                    modifier = Modifier.size(24.dp),
                )
                AssistantAction.Stop -> Icon(
                    painterResource(R.drawable.ic_stop),
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
