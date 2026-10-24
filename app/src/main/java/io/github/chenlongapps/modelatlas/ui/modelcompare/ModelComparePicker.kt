package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.modellist.LabLogo
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListQuery
import io.github.chenlongapps.modelatlas.ui.modellist.filterModels
import io.github.chenlongapps.modelatlas.ui.modellist.labDisplayName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModelComparePicker(
    state: ModelCompareUiState, excludedId: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val results = remember(state.models, search) { filterModels(state.models, ModelListQuery(search = search)) }
    val focus = LocalFocusManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp).testTag("compare-picker"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.compare_picker_title), style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() })
            TextField(
                value = search, onValueChange = { search = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("compare-search"),
                label = { Text(stringResource(R.string.model_search_label)) },
                placeholder = { Text(stringResource(R.string.model_search_hint)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null, Modifier.size(24.dp)) },
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.model_search_clear))
                } },
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            )
            LazyColumn(Modifier.weight(1f).testTag("compare-picker-list"),
                contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.catalogStatus == CompareReadStatus.LOADING -> item {
                        CompareMessage(stringResource(R.string.compare_loading), loading = true)
                    }
                    state.catalogStatus == CompareReadStatus.ERROR -> item {
                        CompareMessage(stringResource(R.string.compare_catalog_error), onRetry)
                    }
                    state.models.isEmpty() -> item { CompareMessage(stringResource(R.string.compare_catalog_empty)) }
                    results.isEmpty() -> item { CompareMessage(stringResource(R.string.model_query_empty)) }
                    else -> items(results, key = { it.id }) { model ->
                        val excluded = model.id == excludedId
                        Surface(
                            onClick = { onSelect(model.id) }, enabled = !excluded,
                            modifier = Modifier.fillMaxWidth().testTag("compare-option:${model.id}"),
                            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LabLogo(model.lab, size = 40.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                                    Text(model.lab?.let(::labDisplayName) ?: stringResource(R.string.no_data),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(model.id, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (excluded) Text(stringResource(R.string.compare_selected), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
