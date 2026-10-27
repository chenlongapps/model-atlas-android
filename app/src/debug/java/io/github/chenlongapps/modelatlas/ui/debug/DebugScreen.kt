package io.github.chenlongapps.modelatlas.ui.debug

import android.content.ClipData
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

internal data class DebugDeviceInfo(
    val versionName: String,
    val versionCode: Long,
    val applicationId: String,
    val debuggable: Boolean,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
)

@Composable
internal fun DebugScreen(
    uiState: DebugUiState,
    info: DebugDeviceInfo,
    onReadStatistics: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDatabase: () -> Unit = {},
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
) {
    val layoutDirection = LocalLayoutDirection.current
    val appRows = listOf(
        stringResource(R.string.debug_version_name) to info.versionName,
        stringResource(R.string.debug_version_code) to info.versionCode.toString(),
        "Application ID" to info.applicationId,
        stringResource(R.string.debug_build_type) to "Debug",
        stringResource(R.string.debug_debuggable) to stringResource(if (info.debuggable) R.string.debug_yes else R.string.debug_no),
    )
    val deviceRows = listOf(
        stringResource(R.string.debug_manufacturer) to info.manufacturer,
        stringResource(R.string.debug_model) to info.model,
        stringResource(R.string.debug_android_version) to info.androidVersion,
        stringResource(R.string.debug_api_level) to info.apiLevel.toString(),
    )
    val statisticsRows = uiState.statistics?.let {
        listOf(
            stringResource(R.string.debug_model_count) to it.models.toString(),
            stringResource(R.string.debug_provider_count) to it.providers.toString(),
            stringResource(R.string.debug_offering_count) to it.offerings.toString(),
        )
    }.orEmpty()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.debug_title)
    val statisticsNote = stringResource(
        if (uiState.statisticsError) R.string.debug_statistics_failed else R.string.debug_statistics_notice,
    )
    val diagnostics = (appRows + deviceRows + statisticsRows).joinToString("\n") { (label, value) -> "$label: $value" } +
        "\n$statisticsNote"

    Scaffold(
        modifier = modifier.testTag("debug-screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().consumeWindowInsets(innerPadding).testTag("debug-content"),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 28.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + bottomOverlayPadding + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Text(
                    title,
                    Modifier.padding(horizontal = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            item(key = "app") {
                DebugSection(stringResource(R.string.debug_app_info)) { InfoRows(appRows) }
            }
            item(key = "device") {
                DebugSection(stringResource(R.string.debug_device_info)) { InfoRows(deviceRows) }
            }
            item(key = "catalog") {
                DebugSection(stringResource(R.string.debug_catalog)) {
                    Text(stringResource(R.string.debug_statistics_notice), style = MaterialTheme.typography.bodySmall)
                    InfoRows(statisticsRows)
                    if (uiState.statistics == null && !uiState.statisticsError && !uiState.isReading) {
                        StatusText(stringResource(R.string.debug_statistics_not_read))
                    }
                    if (uiState.isReading) StatusText(stringResource(R.string.debug_statistics_reading))
                    if (uiState.statisticsError) StatusText(stringResource(R.string.debug_statistics_failed), error = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onReadStatistics, enabled = !uiState.busy) {
                            Text(stringResource(R.string.debug_read_statistics))
                        }
                    }
                }
            }
            item(key = "database") {
                DebugSection(stringResource(R.string.debug_database_entry)) {
                    Text(stringResource(R.string.debug_database_entry_notice), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenDatabase, modifier = Modifier.testTag("debug-database-entry")) {
                        Text(stringResource(R.string.debug_database_entry))
                    }
                }
            }
            item(key = "refresh") {
                DebugSection(stringResource(R.string.debug_data_tools)) {
                    Text(stringResource(R.string.debug_refresh_notice), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRefresh, enabled = !uiState.busy, modifier = Modifier.testTag("debug-refresh")) {
                            Text(stringResource(R.string.debug_refresh))
                        }
                    }
                    if (uiState.isRefreshing) StatusText(stringResource(R.string.debug_refreshing))
                    uiState.refreshResult?.let {
                        StatusText(stringResource(it.messageResource()), error = it != DebugRefreshResult.UPDATED && it != DebugRefreshResult.UNCHANGED)
                    }
                }
            }
            item(key = "copy") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.debug_copy_notice), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, diagnostics)))
                            copied = true
                        }
                    }) { Text(stringResource(R.string.debug_copy)) }
                    if (copied) StatusText(stringResource(R.string.debug_copied))
                }
            }
        }
    }
}

@Composable
private fun DebugSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun InfoRows(rows: List<Pair<String, String>>) {
    rows.forEach { (label, value) ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusText(text: String, error: Boolean = false) {
    Text(
        text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun DebugRefreshResult.messageResource(): Int = when (this) {
    DebugRefreshResult.UPDATED -> R.string.debug_updated
    DebugRefreshResult.UNCHANGED -> R.string.debug_unchanged
    DebugRefreshResult.NETWORK_ERROR -> R.string.debug_network_error
    DebugRefreshResult.SERVER_ERROR -> R.string.debug_server_error
    DebugRefreshResult.INVALID_DATA -> R.string.debug_invalid_data
    DebugRefreshResult.RATE_LIMITED -> R.string.debug_rate_limited
    DebugRefreshResult.STORAGE_ERROR -> R.string.debug_storage_error
}

@Preview(name = "Debug · Light", showBackground = true)
@Preview(name = "Debug · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Debug · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun DebugScreenPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        DebugScreen(
            DebugUiState(statistics = DebugCatalogStatistics(1200, 60, 3200)),
            DebugDeviceInfo("1.0-debug", 1, "io.github.chenlongapps.modelatlas.debug", true, "Google", "Pixel", "17", 37),
            onReadStatistics = {}, onRefresh = {}, bottomOverlayPadding = 96.dp,
        )
    }
}
