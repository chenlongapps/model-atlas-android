package io.github.chenlongapps.modelatlas.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
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
    mainContent: @Composable (isActive: Boolean) -> Unit,
    chatContent: @Composable (isVisible: Boolean, onBack: () -> Unit) -> Unit,
    bottomBar: @Composable BoxScope.(isChatOpen: Boolean, onOpenChat: () -> Unit, backdrop: Backdrop) -> Unit = { _, _, _ -> },
) {
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    if (LocalInspectionMode.current) {
        Box(modifier) {
            mainContent(true)
            bottomBar(false, {}, backdrop)
        }
        return
    }
    val backStack = rememberNavBackStack(MainDestination)
    // 退出端 NavEntry 可能保留旧 content lambda，必须读取最新状态而非捕获旧布尔值。
    val chatVisible by rememberUpdatedState(backStack.last() == AssistantDestination)
    // 弹出助手路由后仍保留阅读／跟随位置；不存对话、草稿或配置。
    val chatReadingState = rememberSaveableStateHolder()
    val openChat: () -> Unit = {
        if (backStack.lastOrNull() == MainDestination) backStack.add(AssistantDestination)
    }
    val closeChat: () -> Unit = {
        if (backStack.lastOrNull() == AssistantDestination) {
            onChatClosed()
            backStack.removeLastOrNull()
        }
    }
    Box(modifier) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
            onBack = closeChat,
            entryProvider = entryProvider {
                entry<MainDestination> {
                    mainContent(!chatVisible)
                }
                entry<AssistantDestination> {
                    // NavDisplay 保留退出端绘制；立即屏蔽其复制等操作、焦点和读屏。
                    Box(Modifier.fillMaxSize().then(
                        if (!chatVisible) Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {} else Modifier,
                    )) {
                        chatReadingState.SaveableStateProvider("assistant-reading") {
                            chatContent(chatVisible, closeChat)
                        }
                        if (!chatVisible) Box(Modifier.matchParentSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        })
                    }
                }
            },
        )
        bottomBar(chatVisible, openChat, backdrop)
    }
}
