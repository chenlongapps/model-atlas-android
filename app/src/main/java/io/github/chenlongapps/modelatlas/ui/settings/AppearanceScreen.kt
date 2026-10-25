package io.github.chenlongapps.modelatlas.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.theme.appearanceColorScheme
import io.github.chenlongapps.modelatlas.ui.theme.hueColor

/** 无注入 Screen；主题状态在 Activity 之上收集，弹层打开时也能全局实时预览。 */
@Composable
internal fun AppearanceScreen(
    uiState: AppearanceUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onMode: (ThemeMode) -> Unit = {},
    onSystemColors: () -> Unit = {},
    onPreset: (ThemePreset) -> Unit = {},
    onRestoreCustom: () -> Unit = {},
    onHue: (Float) -> Unit = {},
    onFlush: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val appearance = uiState.appearance
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Scaffold(
        modifier = modifier.testTag("appearance-screen"),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onFlush(); onBack() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
                Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() })
            }
            LazyColumn(
                Modifier.fillMaxSize().testTag("appearance-content"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (uiState.isLoading || uiState.hasReadError || uiState.hasWriteError) item(key = "status") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(when {
                                uiState.isLoading -> R.string.appearance_loading
                                uiState.hasReadError -> R.string.appearance_read_error
                                else -> R.string.appearance_write_error
                            }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            if (uiState.hasReadError) TextButton(onClick = onRetry) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
                item(key = "mode") { ThemeModeSelector(appearance.mode, uiState.canEdit, onMode) }
                item(key = "colors") {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(R.string.appearance_colors), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            PaletteGrid(appearance, dark, uiState.canEdit, onSystemColors, onPreset, onCustom = {
                                onRestoreCustom()
                                showPicker = true
                            })
                        }
                    }
                }
                item(key = "preview") { AppearancePreviewCard() }
            }
        }
    }
    if (showPicker && uiState.canEdit) {
        HuePickerDialog(
            hue = appearance.customHue ?: AppearanceSettings.InitialCustomHue,
            onHue = onHue,
            onFlush = onFlush,
            onDismiss = { onFlush(); showPicker = false },
        )
    }
}

@Composable
private fun ThemeModeSelector(selected: ThemeMode, enabled: Boolean, onMode: (ThemeMode) -> Unit) {
    val choices = listOf(
        Triple(ThemeMode.Light, R.string.appearance_light, R.drawable.ic_theme_light),
        Triple(ThemeMode.Dark, R.string.appearance_dark, R.drawable.ic_theme_dark),
        Triple(ThemeMode.System, R.string.appearance_system_mode, R.drawable.ic_theme_system),
    )
    BoxWithConstraints {
        val stacked = maxWidth < 280.dp * LocalDensity.current.fontScale
        val item: @Composable (Triple<ThemeMode, Int, Int>, Modifier) -> Unit = { (mode, label, icon), modifier ->
            val active = mode == selected
            Row(
                modifier.clip(MaterialTheme.shapes.extraLarge)
                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(active, enabled = enabled, role = Role.RadioButton, onClick = { onMode(mode) })
                    .testTag("theme-mode-${mode.name}")
                    .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(20.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = color)
            }
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            if (stacked) Column(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                choices.forEach { item(it, Modifier.fillMaxWidth()) }
            } else Row(Modifier.fillMaxWidth().padding(4.dp).selectableGroup()) {
                choices.forEach { item(it, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PaletteGrid(
    appearance: AppearanceSettings,
    dark: Boolean,
    enabled: Boolean,
    onSystem: () -> Unit,
    onPreset: (ThemePreset) -> Unit,
    onCustom: () -> Unit,
) {
    BoxWithConstraints {
        val columns = (maxWidth / (64.dp * LocalDensity.current.fontScale)).toInt().coerceIn(2, 4)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (0..15).toList().chunked(columns).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    indices.forEach { index ->
                        val source = when (index) { 0 -> ThemeColorSource.System; 15 -> ThemeColorSource.Custom; else -> ThemeColorSource.Preset }
                        val preset = ThemePreset.entries.getOrNull(index - 1)
                        val settings = when (source) {
                            ThemeColorSource.System -> appearance.copy(colorSource = source)
                            ThemeColorSource.Preset -> appearance.copy(colorSource = source, preset = requireNotNull(preset))
                            ThemeColorSource.Custom -> appearance.copy(colorSource = source, customHue = appearance.customHue ?: AppearanceSettings.InitialCustomHue)
                        }
                        val colors = appearanceColorScheme(settings, dark)
                        val description = when (source) {
                            ThemeColorSource.System -> stringResource(R.string.appearance_system_colors)
                            ThemeColorSource.Custom -> stringResource(R.string.appearance_custom_colors)
                            ThemeColorSource.Preset -> stringResource(R.string.appearance_preset_description, index)
                        }
                        PaletteTile(
                            colors = colors,
                            selected = appearance.colorSource == source && (source != ThemeColorSource.Preset || appearance.preset == preset),
                            enabled = enabled,
                            description = description,
                            modifier = Modifier.weight(1f).testTag(when (source) {
                                ThemeColorSource.System -> "color-system"
                                ThemeColorSource.Custom -> "color-custom"
                                ThemeColorSource.Preset -> "color-preset-${preset?.name}"
                            }),
                            icon = when (source) { ThemeColorSource.System -> R.drawable.ic_theme_system; ThemeColorSource.Custom -> R.drawable.ic_color_picker; else -> null },
                            label = if (source == ThemeColorSource.System) description else null,
                            onClick = when (source) {
                                ThemeColorSource.System -> onSystem
                                ThemeColorSource.Custom -> onCustom
                                ThemeColorSource.Preset -> { { onPreset(requireNotNull(preset)) } }
                            },
                        )
                    }
                    repeat(columns - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PaletteTile(
    colors: ColorScheme,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Int? = null,
    label: String? = null,
) {
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier.aspectRatio(1f).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(0.78f)) {
            drawArc(colors.primaryContainer, 180f, 180f, useCenter = true)
            drawArc(colors.primary, 90f, 90f, useCenter = true)
            drawArc(colors.secondaryContainer, 0f, 90f, useCenter = true)
        }
        icon?.let {
            Icon(painterResource(it), null, tint = if (colors.primaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(24.dp).background(colors.primaryContainer, CircleShape).padding(2.dp))
        }
        if (selected) Icon(painterResource(R.drawable.ic_check_circle), null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape))
        label?.let {
            Text(it, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(2.dp))
        }
    }
}

@Composable
private fun AppearancePreviewCard() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.appearance_preview_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.appearance_preview_body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(stringResource(R.string.appearance_preview_tag), modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Button(onClick = {}) { Text(stringResource(R.string.appearance_preview_button)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HuePickerDialog(hue: Float, onHue: (Float) -> Unit, onFlush: () -> Unit, onDismiss: () -> Unit) {
    val description = stringResource(R.string.appearance_hue)
    val rainbow = (0..6).map { Color.hsv(it * 60f, 1f, 1f) }.let {
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) it.reversed() else it
    }
    // 配置变化或页面销毁也提交最新预览；不会取消已经开始的写入。
    DisposableEffect(Unit) { onDispose { onFlush() } }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().testTag("hue-picker")) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.appearance_picker_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close)) }
                }
                Slider(
                    value = hue,
                    onValueChange = onHue,
                    onValueChangeFinished = onFlush,
                    valueRange = 0f..360f,
                    modifier = Modifier.fillMaxWidth().testTag("hue-slider").semantics { contentDescription = description },
                    thumb = {
                        Box(Modifier.size(36.dp).shadow(3.dp, CircleShape).background(hueColor(hue), CircleShape).border(3.dp, Color.White, CircleShape))
                    },
                    track = {
                        Box(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(Brush.horizontalGradient(rainbow)))
                    },
                )
            }
        }
    }
}

@Preview(name = "Appearance · Light", showBackground = true)
@Preview(name = "Appearance · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Appearance · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun AppearanceScreenPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        AppearanceScreen(AppearanceUiState(isLoading = false, startupComplete = true), onBack = {})
    }
}
