package io.github.chenlongapps.modelatlas.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R

internal data class DatabaseActions(
    val back: () -> Unit,
    val openTable: (DatabaseTable) -> Unit,
    val search: (String) -> Unit,
    val page: (Int) -> Unit,
    val openRow: (Map<String, String>) -> Unit,
    val create: () -> Unit,
    val edit: () -> Unit,
    val change: (String, String?) -> Unit,
    val save: () -> Unit,
    val requestDelete: () -> Unit,
    val dismissDelete: () -> Unit,
    val delete: () -> Unit,
    val dismissDiscard: () -> Unit,
    val discard: () -> Unit,
    val retry: () -> Unit,
)

@Composable
internal fun DebugDatabaseScreen(state: DatabaseUiState, actions: DatabaseActions) {
    val revealed = remember(state.destination, state.row?.key, state.creating) { mutableStateMapOf<String, Boolean>() }
    val overviewList = rememberLazyListState()
    val tableList = key(state.table) { rememberLazyListState() }
    val detailList = key(state.table, state.selectedKey) { rememberLazyListState() }
    val editList = key(state.table, state.selectedKey, state.creating) { rememberLazyListState() }
    val lastTablePosition = remember(state.table) { mutableStateOf(state.page?.offset to state.search) }
    LaunchedEffect(state.table, state.page?.offset, state.search) {
        val position = state.page?.offset to state.search
        if (state.destination == DatabaseDestination.TABLE && lastTablePosition.value != position) tableList.scrollToItem(0)
        lastTablePosition.value = position
    }
    Scaffold(
        modifier = Modifier.testTag("debug-database-screen").imePadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.back, enabled = !state.saving) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.navigate_back))
                }
                Text(stringResource(R.string.debug_database_title), Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("debug-database-content"),
                state = when (state.destination) {
                    DatabaseDestination.OVERVIEW -> overviewList
                    DatabaseDestination.TABLE -> tableList
                    DatabaseDestination.DETAIL -> detailList
                    DatabaseDestination.EDIT -> editList
                },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { message -> item(key = "message") { DatabaseStatus(stringResource(message)) } }
                if (state.destination != DatabaseDestination.EDIT) {
                    state.error?.let { error ->
                        item(key = "error") {
                            DatabaseStatus(stringResource(error), isError = true)
                            TextButton(onClick = actions.retry) { Text(stringResource(R.string.debug_database_retry)) }
                        }
                    }
                    if (state.saving) item(key = "saving") { DatabaseStatus(stringResource(R.string.debug_database_saving)) }
                }
                if (state.loading) item(key = "loading") { DatabaseStatus(stringResource(R.string.debug_database_loading)) }

                when (state.destination) {
                    DatabaseDestination.OVERVIEW -> {
                        item(key = "overview") {
                            DatabaseSection {
                                Text(stringResource(R.string.debug_database_overview), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.debug_database_name))
                                state.overview?.let { info ->
                                    Text(stringResource(R.string.debug_database_version, info.version))
                                    Text(stringResource(R.string.debug_database_sizes, info.databaseBytes?.toString() ?: "—",
                                        info.walBytes?.toString() ?: "—", info.shmBytes?.toString() ?: "—"))
                                    Text(stringResource(R.string.debug_database_sizes_note), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(stringResource(R.string.debug_database_sync_notice), style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = actions.retry, enabled = !state.loading) { Text(stringResource(R.string.debug_database_reload)) }
                            }
                        }
                        if (state.overview != null && state.error == null) {
                            item(key = "tables-header") { Text(stringResource(R.string.debug_database_tables), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
                            items(DatabaseTable.entries, key = { it.tableName }) { table ->
                                OutlinedButton(onClick = { actions.openTable(table) }, modifier = Modifier.fillMaxWidth().testTag("database-table-${table.tableName}")) {
                                    Text(stringResource(R.string.debug_database_table_count,
                                        "${stringResource(table.titleResource)} (${table.tableName})", state.overview.counts[table]?.toString() ?: "—"))
                                }
                            }
                        }
                    }
                    DatabaseDestination.TABLE -> {
                        val table = state.table
                        if (table != null) {
                            item(key = "table-header") {
                                Text("${stringResource(table.titleResource)} (${table.tableName})", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                OutlinedTextField(
                                    value = state.search, onValueChange = actions.search, modifier = Modifier.fillMaxWidth().testTag("database-search"),
                                    label = { Text(stringResource(R.string.debug_database_search)) }, singleLine = true,
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = actions.create, enabled = !state.loading, modifier = Modifier.testTag("database-add")) {
                                        Text(stringResource(R.string.debug_database_add))
                                    }
                                    OutlinedButton(onClick = actions.retry, enabled = !state.loading) { Text(stringResource(R.string.debug_database_reload)) }
                                }
                            }
                            val page = state.page
                            if (page != null && !state.loading) {
                                if (page.rows.isEmpty()) item(key = "empty") { Text(stringResource(R.string.debug_database_empty)) }
                                items(page.rows, key = { row -> row.key.values.joinToString("") { "${it.length}:$it" } }) { row ->
                                    OutlinedButton(onClick = { actions.openRow(row.key) }, modifier = Modifier.fillMaxWidth().testTag("database-row")) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(row.name, style = MaterialTheme.typography.titleSmall)
                                            Text(row.key.values.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                item(key = "pages") {
                                    val first = if (page.total == 0L) 0 else page.offset + 1
                                    val last = (page.offset.toLong() + page.rows.size).coerceAtMost(page.total)
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(stringResource(R.string.debug_database_page, first, last, page.total))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { actions.page(page.offset - DebugDatabaseStore.PAGE_SIZE) }, enabled = page.offset > 0) {
                                                Text(stringResource(R.string.debug_database_previous))
                                            }
                                            OutlinedButton(onClick = { actions.page(page.offset + DebugDatabaseStore.PAGE_SIZE) }, enabled = last < page.total) {
                                                Text(stringResource(R.string.debug_database_next))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    DatabaseDestination.DETAIL -> {
                        val row = state.row
                        if (row != null) {
                            item(key = "detail-header") {
                                Text("${stringResource(row.table.titleResource)} · ${row.key.values.joinToString(" / ")}",
                                    Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = actions.edit, enabled = !state.loading && !state.saving) { Text(stringResource(R.string.debug_database_edit)) }
                                    OutlinedButton(onClick = actions.requestDelete, enabled = !state.loading && !state.saving) { Text(stringResource(R.string.debug_database_delete)) }
                                }
                            }
                            items(row.table.columns, key = { it.name }) { column ->
                                DatabaseFieldValue(column, row.values[column.name], revealed[column.name] == true) {
                                    revealed[column.name] = revealed[column.name] != true
                                }
                            }
                        }
                    }
                    DatabaseDestination.EDIT -> {
                        val table = state.table
                        if (table != null) {
                            item(key = "edit-header") {
                                Text(stringResource(if (state.creating) R.string.debug_database_add else R.string.debug_database_edit),
                                    Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.debug_database_edit_notice), style = MaterialTheme.typography.bodySmall)
                            }
                            items(table.columns, key = { it.name }) { column ->
                                DatabaseFieldEditor(column, state.draft[column.name], state.creating,
                                    state.fieldError?.field == column.name, revealed[column.name] == true,
                                    onReveal = { revealed[column.name] = revealed[column.name] != true },
                                    onChange = { actions.change(column.name, it) })
                            }
                            item(key = "save") {
                                Button(onClick = actions.save, enabled = !state.saving, modifier = Modifier.testTag("database-save")) {
                                    Text(stringResource(R.string.debug_database_save))
                                }
                                if (state.saving) DatabaseStatus(stringResource(R.string.debug_database_saving))
                                state.fieldError?.let { DatabaseStatus("${it.field}: ${stringResource(it.messageResource)}", isError = true) }
                                state.error?.let { DatabaseStatus(stringResource(it), isError = true) }
                            }
                        }
                    }
                }
            }
        }
    }

    state.deleteImpact?.let { impact ->
        val notice = when (impact.original.table) {
            DatabaseTable.PROVIDERS -> stringResource(R.string.debug_database_delete_provider, impact.cascadeCount)
            DatabaseTable.MODELS -> stringResource(R.string.debug_database_delete_model)
            DatabaseTable.OFFERINGS -> stringResource(R.string.debug_database_delete_offering)
            DatabaseTable.FAVORITES -> stringResource(R.string.debug_database_delete_favorite)
            DatabaseTable.BENCHMARK_SCORES -> stringResource(R.string.debug_database_delete_benchmark_score)
        }
        AlertDialog(
            onDismissRequest = { if (!state.saving) actions.dismissDelete() },
            title = { Text(stringResource(R.string.debug_database_delete_title)) },
            text = { Text(notice) },
            confirmButton = { TextButton(onClick = actions.delete, enabled = !state.saving) { Text(stringResource(R.string.debug_database_delete)) } },
            dismissButton = { TextButton(onClick = actions.dismissDelete, enabled = !state.saving) { Text(stringResource(R.string.debug_database_cancel)) } },
        )
    }
    if (state.confirmDiscard) AlertDialog(
        onDismissRequest = actions.dismissDiscard,
        title = { Text(stringResource(R.string.debug_database_discard_title)) },
        text = { Text(stringResource(R.string.debug_database_discard_notice)) },
        confirmButton = { TextButton(onClick = actions.discard) { Text(stringResource(R.string.debug_database_discard)) } },
        dismissButton = { TextButton(onClick = actions.dismissDiscard) { Text(stringResource(R.string.debug_database_cancel)) } },
    )
}

@Composable
private fun DatabaseSection(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun DatabaseStatus(text: String, isError: Boolean = false) {
    Text(text, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DatabaseFieldValue(column: DatabaseColumn, value: String?, revealed: Boolean, onReveal: () -> Unit) {
    DatabaseSection {
        Text(column.name, style = MaterialTheme.typography.labelLarge)
        if (column.sensitive && value != null) {
            Text(stringResource(R.string.debug_database_sensitive), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onReveal) { Text(stringResource(if (revealed) R.string.debug_database_hide else R.string.debug_database_reveal)) }
        }
        if (!column.sensitive || value == null || revealed) {
            SelectionContainer {
                Text(when (value) {
                    null -> stringResource(R.string.debug_database_null)
                    "" -> stringResource(R.string.debug_database_empty_string)
                    else -> value
                }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DatabaseFieldEditor(
    column: DatabaseColumn, value: String?, creating: Boolean, isError: Boolean, revealed: Boolean,
    onReveal: () -> Unit, onChange: (String?) -> Unit,
) {
    DatabaseSection {
        val type = when {
            column.isJson -> R.string.debug_database_json
            column.type == DatabaseFieldType.INTEGER -> R.string.debug_database_integer
            column.type == DatabaseFieldType.REAL -> R.string.debug_database_real
            column.type == DatabaseFieldType.BOOLEAN -> R.string.debug_database_boolean
            else -> R.string.debug_database_text
        }
        Text(column.name, style = MaterialTheme.typography.labelLarge)
        Text(listOfNotNull(stringResource(type), stringResource(R.string.debug_database_primary).takeIf { column.primary },
            stringResource(R.string.debug_database_required).takeIf { column.required }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
        if (column.primary && !creating) {
            Text(value.orEmpty())
        } else if (column.type == DatabaseFieldType.BOOLEAN) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, "0", "1").forEach { choice ->
                    FilterChip(selected = value == choice, onClick = { onChange(choice) }, label = {
                        Text(stringResource(when (choice) { null -> R.string.debug_database_null; "0" -> R.string.debug_database_false; else -> R.string.debug_database_true }))
                    })
                }
            }
        } else {
            if (!column.required) {
                val valueLabel = stringResource(R.string.debug_database_value_enabled)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = value != null, onCheckedChange = { onChange(if (it) "" else null) },
                        modifier = Modifier.semantics { contentDescription = "${column.name}: $valueLabel" })
                    Text(stringResource(if (value == null) R.string.debug_database_null else R.string.debug_database_value_enabled))
                }
            }
            if (value != null) {
                if (column.sensitive) {
                    Text(stringResource(R.string.debug_database_sensitive), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onReveal) { Text(stringResource(if (revealed) R.string.debug_database_hide else R.string.debug_database_reveal)) }
                }
                if (!column.sensitive || revealed) {
                    val multiline = column.isJson || column.name == "description"
                    OutlinedTextField(
                        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().testTag("database-field-${column.name}"),
                        label = { Text(column.name) }, isError = isError, singleLine = !multiline,
                        minLines = if (column.isJson) 3 else if (multiline) 2 else 1, maxLines = if (multiline) 8 else 1,
                    )
                }
            }
        }
    }
}
