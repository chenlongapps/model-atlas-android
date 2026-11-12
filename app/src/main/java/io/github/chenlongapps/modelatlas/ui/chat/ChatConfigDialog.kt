package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ai.config.ConfigFailure
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

@Composable
internal fun ChatConfigDialog(
    draft: ChatConfigDraft,
    onEdit: (String, String, String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean = false,
    saving: Boolean = false,
    loadFailed: Boolean = false,
    failure: ConfigFailure? = null,
    confirmClear: Boolean = false,
    onReload: () -> Unit = {},
    onRequestClear: () -> Unit = {},
    onCancelClear: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    val busy = loading || saving
    val properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, dismissOnBackPress = !busy, dismissOnClickOutside = !busy)
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = onCancelClear,
            properties = properties,
            title = { Text(stringResource(R.string.chat_config_clear)) },
            text = { Text(stringResource(R.string.chat_config_clear_notice)) },
            confirmButton = { TextButton(onClick = onClear, enabled = !busy, modifier = Modifier.testTag("chat-config-clear-confirm")) {
                Text(stringResource(if (saving) R.string.chat_config_saving else R.string.chat_config_clear_confirm))
            } },
            dismissButton = { TextButton(onClick = onCancelClear, enabled = !busy) { Text(stringResource(R.string.chat_config_cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = properties,
        title = { Text(stringResource(R.string.chat_config_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.chat_config_notice))
                // String TextField 不使用 rememberTextFieldState / Saver；密钥只由 VM 内存草稿持有。
                OutlinedTextField(
                    value = draft.baseUrl,
                    enabled = !busy && !loadFailed,
                    onValueChange = { onEdit(it, draft.apiKey, draft.modelId) },
                    label = { Text(stringResource(R.string.chat_base_url)) },
                    supportingText = { Text(stringResource(R.string.chat_base_url_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("chat-base-url"),
                )
                OutlinedTextField(
                    value = draft.apiKey,
                    enabled = !busy && !loadFailed,
                    onValueChange = { onEdit(draft.baseUrl, it, draft.modelId) },
                    label = { Text(stringResource(R.string.chat_api_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("chat-api-key"),
                )
                OutlinedTextField(
                    value = draft.modelId,
                    enabled = !busy && !loadFailed,
                    onValueChange = { onEdit(draft.baseUrl, draft.apiKey, it) },
                    label = { Text(stringResource(R.string.chat_model_id)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth().testTag("chat-model-id"),
                )
                if (draft.invalid) Text(stringResource(R.string.chat_config_invalid), color = MaterialTheme.colorScheme.error)
                if (failure != null) ChatConfigFailure(failure)
                if (loadFailed) TextButton(onClick = onReload, enabled = !busy) { Text(stringResource(R.string.chat_config_reload)) }
                TextButton(onClick = onRequestClear, enabled = !busy, modifier = Modifier.testTag("chat-config-clear")) {
                    Text(stringResource(R.string.chat_config_clear))
                }
            }
        },
        confirmButton = { TextButton(onClick = onApply, enabled = !busy && !loadFailed, modifier = Modifier.testTag("chat-config-apply")) {
            Text(stringResource(if (saving) R.string.chat_config_saving else R.string.chat_config_apply))
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.chat_config_cancel)) } },
    )
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
    ModelAtlasTheme(dynamicColor = false) { ChatConfigDialog(ChatConfigDraft(), { _, _, _ -> }, {}, {}) }
}
