package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ChatRoute(
    bottomOverlayPadding: Dp = 0.dp,
    isVisible: Boolean = true,
    onBack: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 返回确认由宿主同步停止；动画退出端的可见性变化同样停止，配置重建不取消。
    LaunchedEffect(viewModel, isVisible) { if (!isVisible) viewModel.stop() }
    ChatScreen(
        uiState = state,
        bottomOverlayPadding = bottomOverlayPadding,
        isVisible = isVisible,
        onBack = onBack,
        onInput = viewModel::setInput,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRetry = viewModel::retry,
        onOpenConfig = viewModel::openConfig,
        onEditConfig = viewModel::editConfig,
        onApplyConfig = viewModel::applyConfig,
        onDismissConfig = viewModel::dismissConfig,
        onReloadConfig = viewModel::retryLoadConfig,
        onRequestClearConfig = viewModel::requestClearConfig,
        onCancelClearConfig = viewModel::cancelClearConfig,
        onClearConfig = viewModel::clearConfig,
    )
}
