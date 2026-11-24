package io.github.chenlongapps.modelatlas.ui.chat

import android.content.res.Configuration
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
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
import io.github.chenlongapps.modelatlas.ai.LlmConfigField
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
        var submitCount by remember { mutableIntStateOf(0) }
        val urlField = remember { BringIntoViewRequester() }
        val keyField = remember { BringIntoViewRequester() }
        val modelField = remember { BringIntoViewRequester() }
        val urlInvalid = LlmConfigField.BASE_URL in draft.invalidFields
        val keyInvalid = LlmConfigField.API_KEY in draft.invalidFields
        val modelInvalid = LlmConfigField.MODEL_ID in draft.invalidFields
        val colors = MaterialTheme.colorScheme
        val fieldColors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLow,
            unfocusedContainerColor = colors.surfaceContainerLow,
            disabledContainerColor = colors.surfaceContainerLow,
            errorContainerColor = colors.surfaceContainerLow,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        )
        val fieldShape = MaterialTheme.shapes.medium
        LaunchedEffect(failure) {
            if (failure != null) scroll.scrollTo(0)
        }
        LaunchedEffect(submitCount) {
            if (submitCount > 0) {
                val firstInvalid = when {
                    urlInvalid -> urlField
                    keyInvalid -> keyField
                    modelInvalid -> modelField
                    else -> null
                }
                firstInvalid?.bringIntoView()
            }
        }
        val showForm = !loading && !loadFailed
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .padding(bottom = if (showForm) 0.dp else 12.dp).testTag("chat-config-sheet"),
        ) {
            // ModalBottomSheet 已处理 IME 和安全区；标题可随表单滚动，为短视口腾出空间。
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(scroll).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.chat_config_title), style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close))
                    }
                }
                when {
                    loading -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(R.string.chat_config_loading),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    loadFailed -> {
                        failure?.let { ChatConfigFailure(it) }
                        Column {
                            TextButton(
                                onClick = onReload,
                                enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.chat_config_reload)) }
                            TextButton(
                                onClick = { hideKeyboard(); onRequestClear() },
                                enabled = !busy,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.chat_config_reconfigure)) }
                        }
                    }
                    else -> {
                        failure?.let { ChatConfigFailure(it) }
                        // String TextField 不使用 rememberTextFieldState / Saver；密钥只由 VM 内存草稿持有。
                        TextField(
                            value = draft.baseUrl,
                            enabled = !busy,
                            onValueChange = { onEdit(it, draft.apiKey, draft.modelId) },
                            shape = fieldShape,
                            colors = fieldColors,
                            label = { Text(stringResource(R.string.chat_base_url)) },
                            isError = urlInvalid,
                            supportingText = if (urlInvalid || showUrlHelp) ({
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (urlInvalid) Text(stringResource(R.string.chat_base_url_invalid))
                                    if (showUrlHelp) ConfigSupportingText(R.string.chat_base_url_hint)
                                }
                            }) else null,
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
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(urlField).testTag("chat-base-url"),
                        )
                        TextField(
                            value = draft.apiKey,
                            enabled = !busy,
                            onValueChange = { onEdit(draft.baseUrl, it, draft.modelId) },
                            shape = fieldShape,
                            colors = fieldColors,
                            label = { Text(stringResource(R.string.chat_api_key)) },
                            isError = keyInvalid,
                            supportingText = if (keyInvalid) ({ Text(stringResource(R.string.chat_api_key_invalid)) }) else null,
                            trailingIcon = {
                                IconButton(onClick = { keyVisible = !keyVisible }, enabled = !busy, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        painterResource(if (keyVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                                        stringResource(if (keyVisible) R.string.chat_api_key_hide else R.string.chat_api_key_show),
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Next),
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(keyField).testTag("chat-api-key"),
                        )
                        TextField(
                            value = draft.modelId,
                            enabled = !busy,
                            onValueChange = { onEdit(draft.baseUrl, draft.apiKey, it) },
                            shape = fieldShape,
                            colors = fieldColors,
                            label = { Text(stringResource(R.string.chat_model_id)) },
                            isError = modelInvalid,
                            supportingText = if (modelInvalid) ({ Text(stringResource(R.string.chat_model_id_invalid)) }) else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(modelField).testTag("chat-model-id"),
                        )
                    }
                }
            }
            if (showForm) {
                Button(
                    onClick = {
                        keyVisible = false
                        hideKeyboard()
                        onApply()
                        submitCount++
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp).heightIn(min = 48.dp)
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
        containerColor = MaterialTheme.colorScheme.surface,
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
    Text(
        stringResource(when (failure) {
            ConfigFailure.STORAGE -> R.string.chat_config_storage_error
            ConfigFailure.KEY_UNAVAILABLE -> R.string.chat_config_key_error
        }),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Preview(name = "Chat configuration · light", showBackground = true)
@Preview(name = "Chat configuration · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Chat configuration · large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ChatConfigPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ChatConfigSheet(ChatConfigDraft(), { _, _, _ -> }, {}, {})
        }
    }
}
