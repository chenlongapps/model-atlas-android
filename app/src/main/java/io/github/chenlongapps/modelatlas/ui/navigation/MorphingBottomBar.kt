package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.chat.ChatUiState
import io.github.chenlongapps.modelatlas.ui.components.glass.GlassStyle
import io.github.chenlongapps.modelatlas.ui.components.glass.glassEffectsAvailable
import io.github.chenlongapps.modelatlas.ui.components.glass.glassSurface

/** 底栏由当前导航路由决定形态，不维护独立的开关状态。 */
internal enum class BottomBarMode { Navigation, Chat }

private val composerGlassStyle = GlassStyle.BottomBar.copy(tintAlpha = 0.86f)
private val actionGlassStyle = GlassStyle.BottomBar.copy(tintAlpha = 0.9f)
private val darkNavigationGlassStyle = GlassStyle.BottomBar.copy(tintAlpha = 0.68f)
private const val AssistantActionDurationMillis = 180
private val capsuleTintAnimation = tween<Color>(220)
private val actionTintAnimation = tween<Color>(AssistantActionDurationMillis)

private fun navigationGlassStyle(surface: Color): GlassStyle =
    if (surface.luminance() < 0.5f) darkNavigationGlassStyle else GlassStyle.BottomBar

/**
 * 主界面与助手共用的唯一底栏：胶囊玻璃表面保持不变，内部内容与末侧按钮随模式软化过渡。
 * 导航模式下胶囊在起始侧、AI 入口在末侧；聊天模式下胶囊成为输入栏，圆钮切换操作。
 */
@Composable
internal fun MorphingBottomBar(
    mode: BottomBarMode,
    pagerState: PagerState,
    items: List<GlassNavigationItem>,
    backdrop: Backdrop?,
    chatState: State<ChatUiState>,
    onNavigate: suspend (Int) -> Unit,
    onModelsDoubleClick: () -> Unit,
    onToggleChat: () -> Unit,
    onChatInput: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    navigationEnabled: Boolean = true,
    onActionAnchor: ((AssistantRevealAnchor) -> Unit)? = null,
    onNavigationHeight: ((Dp) -> Unit)? = null,
) {
    require(items.isNotEmpty())
    val spacing = if (items.size >= 5) 2.dp else 4.dp
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val lensMotion = remember(pagerState, scope) { NavigationLensMotion(pagerState, scope) }
    LaunchedEffect(lensMotion) { lensMotion.followPagerGestures() }
    val modelsDoubleClick by rememberUpdatedState {
        lensMotion.select(0, onNavigate)
        onModelsDoubleClick()
    }
    val colors = MaterialTheme.colorScheme
    val chatOpen = mode == BottomBarMode.Chat
    val transition = updateTransition(mode, label = "Bottom bar mode")
    val glassBackdrop = if (glassEffectsAvailable()) backdrop else null
    val plateBackdrop = if (glassBackdrop != null &&
        (mode == BottomBarMode.Navigation || transition.currentState == BottomBarMode.Navigation)
    ) rememberLayerBackdrop() else null
    // A retained display list lets the lens replay the plate's already-filtered pixels.
    val plateCapture = if (plateBackdrop != null) Modifier.layerBackdrop(plateBackdrop).graphicsLayer() else Modifier
    val capsuleSurface = if (chatOpen) colors.surfaceContainerHigh else colors.surfaceContainer
    val density = LocalDensity.current
    // 记住导航内容的实测高度，让输入胶囊在默认字号下与导航胶囊完全同高。
    var navigationContentHeight by rememberSaveable { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier.testTag("glass-navigation")) {
        val gap = 8.dp
        val capsuleMinWidth = (NavigationMinTouchTarget + spacing) * items.size -
            spacing + NavigationCapsuleInset * 2
        val aiSize = (maxWidth - gap - capsuleMinWidth).coerceIn(NavigationMinTouchTarget, 64.dp)
        val navigationHeight = maxOf(aiSize, navigationContentHeight.dp)
        val navigationAligned = !chatOpen && transition.currentState == BottomBarMode.Navigation
        if (onNavigationHeight != null && navigationContentHeight > 0f) {
            SideEffect { onNavigationHeight(navigationHeight) }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(mode, layoutDirection, items.size, spacing, aiSize, enabled, navigationEnabled) {
                    if (!enabled || !navigationEnabled || mode != BottomBarMode.Navigation) return@pointerInput
                    detectModelsDoubleClicks({ width ->
                        navigationModelsRange(
                            width, aiSize.toPx(), gap.toPx(), NavigationCapsuleInset.toPx(), spacing.toPx(), items.size, isRtl,
                        )
                    }) { modelsDoubleClick() }
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .testTag("glass-navigation-capsule"),
            ) {
                Spacer(
                    Modifier.matchParentSize().then(plateCapture).glassSurface(
                        backdrop = glassBackdrop,
                        shape = CircleShape,
                        style = if (chatOpen) composerGlassStyle else navigationGlassStyle(colors.surface),
                        tint = if (chatOpen) capsuleSurface else colors.surface,
                        fallbackColor = capsuleSurface,
                        tintAnimationSpec = capsuleTintAnimation,
                    ),
                )
                transition.AnimatedContent(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart,
                    transitionSpec = {
                        (fadeIn(tween(durationMillis = 220, delayMillis = 40)) togetherWith fadeOut(tween(durationMillis = 140)))
                            .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> tween(durationMillis = 220) }))
                    },
                ) { target ->
                    // 退出端立即停用交互与读屏，动画期间只有目标形态可操作。
                    val interactive = enabled && mode == target &&
                        (target == BottomBarMode.Chat || navigationEnabled)
                    when (target) {
                        BottomBarMode.Navigation -> NavigationCapsuleContent(
                            pagerState = pagerState,
                            motion = lensMotion,
                            backdrop = glassBackdrop,
                            materialBackdrop = plateBackdrop,
                            onNavigate = onNavigate,
                            onModelsDoubleClick = { modelsDoubleClick() },
                            items = items,
                            minHeight = aiSize,
                            enabled = interactive,
                            modifier = Modifier
                                .interactionGate(interactive)
                                .onSizeChanged { navigationContentHeight = with(density) { it.height.toDp().value } },
                        )
                        BottomBarMode.Chat -> ChatComposerContent(
                            chatState = chatState,
                            enabled = interactive,
                            onInput = onChatInput,
                            minHeight = navigationHeight,
                            modifier = Modifier.interactionGate(interactive),
                        )
                    }
                }
            }
            // 导航时随整行居中；编辑器增高时，按钮仍居中于贴底的导航槽位。
            Box(
                modifier = if (navigationAligned) Modifier else Modifier
                    .align(Alignment.Bottom)
                    .heightIn(min = navigationHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(aiSize).then(
                        if (onActionAnchor == null) Modifier else Modifier.onGloballyPositioned { coordinates ->
                            val radius = coordinates.size.width / 2f
                            onActionAnchor(AssistantRevealAnchor(coordinates.localToRoot(Offset(radius, radius)), radius))
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    AssistantActionButton(
                        chatOpen = chatOpen,
                        chatState = chatState,
                        slotSize = aiSize,
                        backdrop = glassBackdrop,
                        enabled = enabled,
                        onToggleChat = onToggleChat,
                        onSend = onSend,
                        onStop = onStop,
                    )
                }
            }
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
    val minFieldHeight = 44.dp
    val verticalPadding = ((minHeight - minFieldHeight) / 2).coerceIn(0.dp, 12.dp)
    val fieldHeight = minHeight - verticalPadding * 2
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 单行胶囊与导航胶囊同高；长文本有上限并在编辑器内部滚动。
        val maxFieldHeight = (maxHeight * 0.3f).coerceIn(minFieldHeight, 140.dp).coerceAtLeast(fieldHeight)
        BasicTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = verticalPadding)
                .heightIn(min = fieldHeight, max = maxFieldHeight)
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
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

private enum class AssistantAction { Ai, Back, Send, Stop }

@Composable
private fun AssistantActionButton(
    chatOpen: Boolean,
    chatState: State<ChatUiState>,
    slotSize: Dp,
    backdrop: Backdrop?,
    enabled: Boolean,
    onToggleChat: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    // 只订阅按钮所需的状态，普通输入和流式回复不重组玻璃表面。
    val canSend by remember(chatState) { derivedStateOf { chatState.value.canSend } }
    val isGenerating by remember(chatState) { derivedStateOf { chatState.value.isGenerating } }
    val canReturn by remember(chatState) { derivedStateOf { chatState.value.canReturnFromComposer } }
    val action = when {
        !chatOpen -> AssistantAction.Ai
        isGenerating -> AssistantAction.Stop
        canReturn -> AssistantAction.Back
        else -> AssistantAction.Send
    }
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val label = stringResource(
        when (action) {
            AssistantAction.Ai -> R.string.chat_title
            AssistantAction.Back -> R.string.chat_exit
            AssistantAction.Send -> R.string.chat_send
            AssistantAction.Stop -> R.string.chat_stop
        },
    )
    val clickable = enabled && (action != AssistantAction.Send || canSend)
    val requestAction = action == AssistantAction.Send || action == AssistantAction.Stop
    val size by animateDpAsState(
        if (requestAction) slotSize.coerceAtMost(56.dp) else slotSize,
        tween(AssistantActionDurationMillis),
        label = "Assistant action size",
    )
    val emphasized = clickable && requestAction
    val actionSurface = when {
        emphasized -> colors.primaryContainer
        chatOpen -> colors.surfaceContainerHigh
        else -> colors.surfaceContainer
    }
    val iconTint by animateColorAsState(
        if (emphasized) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.38f),
        actionTintAnimation,
        label = "Assistant action tint",
    )
    val tag = when (action) {
        AssistantAction.Ai -> "nav-chat"
        AssistantAction.Back -> "chat-exit"
        AssistantAction.Send -> "chat-send"
        AssistantAction.Stop -> "chat-stop"
    }
    Box(
        modifier = Modifier
            .size(size)
            .glassSurface(
                backdrop = backdrop,
                shape = CircleShape,
                style = if (chatOpen) actionGlassStyle else navigationGlassStyle(colors.surface),
                tint = if (chatOpen) actionSurface else colors.surface,
                fallbackColor = actionSurface,
                tintAnimationSpec = actionTintAnimation,
            )
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
                    AssistantAction.Ai, AssistantAction.Back -> onToggleChat()
                    AssistantAction.Send -> onSend()
                    AssistantAction.Stop -> onStop()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = action,
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val spec = tween<Float>(AssistantActionDurationMillis)
                ((fadeIn(spec) + scaleIn(spec, initialScale = 0.85f)) togetherWith
                    (fadeOut(spec) + scaleOut(spec, targetScale = 0.85f))).using(null)
            },
            modifier = Modifier.clearAndSetSemantics {},
            label = "Assistant action",
        ) { target ->
            when (target) {
                AssistantAction.Ai -> NavigationItemContent(
                    label = stringResource(R.string.nav_chat),
                    color = colors.onSurfaceVariant,
                ) { iconModifier ->
                    Icon(
                        painterResource(R.drawable.ic_assistant_sparkle),
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = iconModifier,
                    )
                }
                AssistantAction.Back -> Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                AssistantAction.Send, AssistantAction.Stop -> Icon(
                    painterResource(if (target == AssistantAction.Send) R.drawable.ic_send else R.drawable.ic_stop),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
