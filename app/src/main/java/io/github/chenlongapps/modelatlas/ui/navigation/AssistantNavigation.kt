package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEventTransitionState
import com.kyant.backdrop.Backdrop
import io.github.chenlongapps.modelatlas.ui.components.glass.captureGlassBackdrop
import io.github.chenlongapps.modelatlas.ui.components.glass.rememberGlassBackdrop
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

@Serializable
private data object MainDestination : NavKey

@Serializable
private data object AssistantDestination : NavKey

/**
 * 只管理主界面与助手的独立路由，不接管既有详情／榜单共享转场。
 * 共享底栏绘制在 NavDisplay 之外，路由切换时容器不会重建或闪烁。
 */
@Composable
internal fun AssistantNavigation(
    modifier: Modifier = Modifier,
    onChatClosed: () -> Unit,
    revealAnchor: () -> AssistantRevealAnchor?,
    mainContent: @Composable (isActive: Boolean) -> Unit,
    chatContent: @Composable (isVisible: Boolean, onBack: () -> Unit) -> Unit,
    bottomBar: @Composable BoxScope.(isChatOpen: Boolean, interactive: Boolean, onOpenChat: () -> Unit, backdrop: Backdrop?) -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val backdrop = rememberGlassBackdrop(background)
    if (LocalInspectionMode.current) {
        Box(modifier) {
            mainContent(true)
            bottomBar(false, true, {}, backdrop)
        }
        return
    }
    val backStack = rememberNavBackStack(MainDestination)
    val chatOnStack = backStack.last() == AssistantDestination
    // 只保存已确认的退出意图，不保存入场或手势进度。重建时停在对应端点。
    var closing by rememberSaveable { mutableStateOf(false) }
    val reveal = remember { AssistantReveal(chatOnStack, closing) }
    val chatVisible = chatOnStack && !closing
    // 弹出助手路由后仍保留阅读／跟随位置；不存对话、草稿或配置。
    val chatReadingState = rememberSaveableStateHolder()
    val openChat: () -> Unit = {
        if (backStack.last() == MainDestination && !closing) {
            reveal.phase = RevealPhase.Opening
            backStack.add(AssistantDestination)
        }
    }
    val closeChat: () -> Unit = {
        if (backStack.last() == AssistantDestination && !closing) {
            closing = true
            reveal.phase = RevealPhase.Closing
            onChatClosed()
        }
    }
    val phase = reveal.phase
    LaunchedEffect(phase) {
        when (phase) {
            RevealPhase.Opening, RevealPhase.Restoring -> reveal.settle(phase, 1f, 420)
            RevealPhase.Closing -> {
                reveal.settle(phase, 0f, 360)
                backStack.removeLastOrNull()
                closing = false
            }
            RevealPhase.Idle, RevealPhase.Dragging -> Unit
        }
    }
    val currentAnchor by rememberUpdatedState(revealAnchor)
    val currentBackground by rememberUpdatedState(background)
    val latestChatContent by rememberUpdatedState(chatContent)
    val latestMainContent by rememberUpdatedState(mainContent)
    val latestCloseChat by rememberUpdatedState(closeChat)
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<MainDestination> {
                latestMainContent(backStack.last() == MainDestination)
            }
            entry<AssistantDestination> {
                // 同 key 的旧 Scene 可能在弹栈后再绘制一帧，不重新开启助手交互。
                val visible = backStack.last() == AssistantDestination && !closing
                var position by remember { mutableStateOf(Offset.Zero) }
                Box(
                    Modifier.fillMaxSize()
                        .onGloballyPositioned { position = it.positionInRoot() }
                        .circularReveal({ reveal.progress }, { currentAnchor() }, { position })
                        .background(currentBackground)
                        .then(if (!visible) Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {} else Modifier),
                ) {
                    chatReadingState.SaveableStateProvider("assistant-reading") {
                        latestChatContent(visible) { latestCloseChat() }
                    }
                    if (!visible) Box(Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    })
                }
            }
        },
    )
    val strategies = remember(reveal) { listOf(AssistantSceneStrategy(reveal)) }
    val scene = rememberSceneState(entries, strategies, onBack = closeChat)
    val back = rememberNavigationEventState(scene)
    // 使用 Navigation 3 的场景返回处理器；拖动只寻址圆形裁剪，不切到上一场景。
    NavigationBackHandler(
        sceneState = scene,
        state = back,
        onBackCancelled = {
            if (reveal.phase == RevealPhase.Dragging) reveal.phase = RevealPhase.Restoring
        },
        onBackCompleted = closeChat,
    )
    LaunchedEffect(back) {
        snapshotFlow { back.transitionState }.collect { state ->
            if (state is NavigationEventTransitionState.InProgress &&
                coroutineContext[MotionDurationScale]?.scaleFactor != 0f && !closing
            ) {
                reveal.seekBack(state.latestEvent.progress)
            }
        }
    }
    Box(modifier) {
        NavDisplay(
            sceneState = scene,
            // 不把手势交给 NavDisplay 的场景动画，否则会提前切走同 key 的助手内容。
            navigationEventState = rememberNavigationEventState(scene),
            modifier = Modifier.fillMaxSize().captureGlassBackdrop(backdrop),
            transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
            popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
        )
        bottomBar(chatVisible, !closing, openChat, backdrop)
    }
}

/** Main 的场景身份不变，打开助手只在原布局上方增加一个独立 NavEntry。 */
private data class AssistantScene(
    override val entries: List<NavEntry<NavKey>>,
    private val reveal: AssistantReveal,
) : Scene<NavKey> {
    override val key = entries.first().contentKey
    override val previousEntries = entries.dropLast(1)
    override val content: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            val chat = entries.getOrNull(1)
            Box(Modifier.fillMaxSize().drawWithContent {
                if (chat == null || reveal.progress < 1f) drawContent()
            }) {
                entries.first().Content()
            }
            chat?.Content()
        }
    }
}

private class AssistantSceneStrategy(private val reveal: AssistantReveal) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey> =
        AssistantScene(entries, reveal)
}

private enum class RevealPhase { Idle, Opening, Dragging, Restoring, Closing }

private class AssistantReveal(open: Boolean, closing: Boolean) {
    var phase by mutableStateOf(if (closing) RevealPhase.Closing else RevealPhase.Idle)
    var progress by mutableFloatStateOf(if (open && !closing) 1f else 0f)
        private set
    private var dragStart = 1f

    fun seekBack(backProgress: Float) {
        if (phase != RevealPhase.Dragging) dragStart = progress
        phase = RevealPhase.Dragging
        progress = dragStart * (1f - backProgress.coerceIn(0f, 1f))
    }

    suspend fun settle(command: RevealPhase, target: Float, durationMillis: Int) {
        val duration = (durationMillis * abs(target - progress)).roundToInt()
        if (duration > 0) {
            animate(progress, target, animationSpec = tween(duration, easing = FastOutSlowInEasing)) { value, _ ->
                if (phase == command) progress = value
            }
        }
        if (phase == command) {
            progress = target
            phase = RevealPhase.Idle
        }
    }
}

internal data class AssistantRevealAnchor(val center: Offset, val radius: Float)

internal fun assistantRevealEndRadius(center: Offset, size: Size): Float =
    hypot(maxOf(abs(center.x), abs(size.width - center.x)), maxOf(abs(center.y), abs(size.height - center.y)))

private fun Modifier.circularReveal(
    progress: () -> Float,
    anchor: () -> AssistantRevealAnchor?,
    position: () -> Offset,
): Modifier = drawWithCache {
    val circle = Path()
    onDrawWithContent {
        val fraction = progress()
        val origin = if (fraction < 1f) anchor() else null
        if (origin == null) {
            drawContent()
        } else {
            val center = origin.center - position()
            val radius = lerp(origin.radius, maxOf(origin.radius, assistantRevealEndRadius(center, size)), fraction)
            circle.rewind()
            circle.addOval(Rect(center, radius))
            clipPath(circle) { this@onDrawWithContent.drawContent() }
        }
    }
}
