package io.github.chenlongapps.modelatlas.ui.chat

import android.view.WindowInsets as PlatformWindowInsets
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ai.config.ConfigFailure
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
internal fun ChatConfigSheet(
    draft: ChatConfigDraft,
    onEdit: (String, String, String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    saving: Boolean = false,
    loadFailed: Boolean = false,
    failure: ConfigFailure? = null,
    confirmClear: Boolean = false,
    startsNewConversation: Boolean = false,
    onReload: () -> Unit = {},
    onRequestClear: () -> Unit = {},
    onCancelClear: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    val busy = loading || saving
    if (loadFailed && confirmClear) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            properties = DialogProperties(
                securePolicy = SecureFlagPolicy.SecureOn, dismissOnBackPress = !busy, dismissOnClickOutside = !busy,
            ),
            title = { Text(stringResource(R.string.chat_config_reconfigure)) },
            text = { Text(stringResource(R.string.chat_config_reconfigure_notice)) },
            confirmButton = { TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.testTag("chat-config-clear-confirm")) {
                Text(stringResource(if (saving) R.string.chat_config_saving else R.string.chat_config_reconfigure_confirm))
            } },
            dismissButton = { TextButton(onClick = onCancelClear, enabled = !busy) { Text(stringResource(R.string.chat_config_cancel)) } },
        )
        return
    }
    val content: @Composable () -> Unit = {
        val scroll = rememberScrollState()
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val hideKeyboard = { focus.clearFocus(); keyboard?.hide() }
        // 仅在当前窗口内临时显示；重建、关闭和清除确认后恢复掩码。
        var keyVisible by remember { mutableStateOf(false) }
        var showUrlHelp by remember { mutableStateOf(false) }
        val editable = !busy && !loadFailed
        LaunchedEffect(draft.invalid, failure) {
            if (draft.invalid || failure != null) scroll.scrollTo(0)
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("chat-config-sheet")) {
            // ModalBottomSheet 已处理 IME 和安全区；只有表单滚动，提交按钮保留在可见区域。
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.chat_config_title), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                }
                if (draft.invalid) {
                    Text(
                        stringResource(R.string.chat_config_invalid), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (failure != null) ChatConfigFailure(failure)
                if (loadFailed) {
                    TextButton(onClick = onReload, enabled = !busy) { Text(stringResource(R.string.chat_config_reload)) }
                    TextButton(onClick = { hideKeyboard(); onRequestClear() }, enabled = !busy) {
                        Text(stringResource(R.string.chat_config_reconfigure))
                    }
                }
                // String TextField 不使用 rememberTextFieldState / Saver；密钥只由 VM 内存草稿持有。
                OutlinedTextField(
                    value = draft.baseUrl,
                    enabled = editable,
                    onValueChange = { onEdit(it, draft.apiKey, draft.modelId) },
                    label = { Text(stringResource(R.string.chat_base_url)) },
                    trailingIcon = {
                        IconButton(onClick = { showUrlHelp = !showUrlHelp }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_info),
                                stringResource(if (showUrlHelp) R.string.chat_base_url_help_hide else R.string.chat_base_url_help_show),
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("chat-base-url"),
                )
                if (showUrlHelp) ConfigSupportingText(R.string.chat_base_url_hint)
                OutlinedTextField(
                    value = draft.apiKey,
                    enabled = editable,
                    onValueChange = { onEdit(draft.baseUrl, it, draft.modelId) },
                    label = { Text(stringResource(R.string.chat_api_key)) },
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }, enabled = editable, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painterResource(if (keyVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                stringResource(if (keyVisible) R.string.chat_api_key_hide else R.string.chat_api_key_show),
                            )
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("chat-api-key"),
                )
                OutlinedTextField(
                    value = draft.modelId,
                    enabled = editable,
                    onValueChange = { onEdit(draft.baseUrl, draft.apiKey, it) },
                    label = { Text(stringResource(R.string.chat_model_id)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("chat-model-id"),
                )
            }
            Button(
                onClick = { keyVisible = false; hideKeyboard(); onApply() }, enabled = editable,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).heightIn(min = 48.dp)
                    .testTag("chat-config-apply"),
            ) {
                Text(stringResource(when {
                    saving -> R.string.chat_config_saving
                    startsNewConversation -> R.string.chat_config_apply_new_conversation
                    else -> R.string.chat_config_apply
                }))
            }
        }
    }
    if (LocalInspectionMode.current) {
        content()
        return
    }
    val isBusy by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !isBusy },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        sheetState = sheetState,
        sheetMaxWidth = 560.dp,
        sheetGesturesEnabled = !busy,
        dragHandle = null,
        contentWindowInsets = { WindowInsets.safeDrawing },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.SecureOn,
            shouldDismissOnBackPress = false,
            shouldDismissOnClickOutside = !busy,
        ),
    ) {
        // 在面板窗口内按最新的 IME 状态处理返回，避免两次快速返回都被旧的 inset 状态消费。
        val view = LocalView.current
        val imeVisible = WindowInsets.isImeVisible
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val scope = rememberCoroutineScope()
        NavigationBackHandler(
            state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
            onBackCompleted = {
                if (view.rootWindowInsets?.isVisible(PlatformWindowInsets.Type.ime()) ?: imeVisible) {
                    focus.clearFocus()
                    keyboard?.hide()
                } else if (!isBusy) {
                    scope.launch {
                        sheetState.hide()
                        if (!sheetState.isVisible && !isBusy) onDismiss()
                    }
                }
            },
        )
        content()
    }
}

@Composable
private fun ConfigSupportingText(@StringRes label: Int) {
    Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ChatConfigFailure(failure: ConfigFailure) {
    Text(stringResource(when (failure) {
        ConfigFailure.STORAGE -> R.string.chat_config_storage_error
        ConfigFailure.KEY_UNAVAILABLE -> R.string.chat_config_key_error
    }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Preview(name = "Chat configuration", showBackground = true)
@Composable
private fun ChatConfigPreview() {
    ModelAtlasTheme(dynamicColor = false) { ChatConfigSheet(ChatConfigDraft(), { _, _, _ -> }, {}, {}) }
}
