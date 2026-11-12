package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ChatRoute(onBack: () -> Unit, viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ChatScreen(
        uiState = state,
        onBack = { viewModel.stop(); onBack() },
        onInput = viewModel::setInput,
        onSend = viewModel::send,
        onStop = viewModel::stop,
        onRetry = viewModel::retry,
        onOpenConfig = viewModel::openConfig,
        onEditConfig = viewModel::editConfig,
        onApplyConfig = viewModel::applyConfig,
        onDismissConfig = viewModel::dismissConfig,
    )
}
