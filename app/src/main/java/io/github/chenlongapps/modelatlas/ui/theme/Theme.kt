package io.github.chenlongapps.modelatlas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import io.github.chenlongapps.modelatlas.data.model.AppearanceSettings
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource

private val LightColorScheme = lightColorScheme()
private val DarkColorScheme = darkColorScheme()
private val AppTypography = Typography().let { defaults ->
    defaults.copy(
        headlineMedium = defaults.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ModelAtlasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    appearance: AppearanceSettings? = null,
    content: @Composable () -> Unit,
) {
    val effectiveDark = appearance?.mode?.isDark(darkTheme) ?: darkTheme
    val colorScheme = appearanceColorScheme(appearance ?: AppearanceSettings(), effectiveDark, dynamicColor)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** 主题与网格预览共用同一解析路径；预览宿主不参与系统取色。 */
@Composable
internal fun appearanceColorScheme(
    appearance: AppearanceSettings,
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
): ColorScheme {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val source = appearance.colorSource
    if (source != ThemeColorSource.System) {
        val color = if (source == ThemeColorSource.Custom) hueColor(requireNotNull(appearance.customHue))
            else Color(appearance.preset.argb)
        val muted = source == ThemeColorSource.Preset && appearance.preset.muted
        return remember(color, muted, darkTheme) { generatedColorScheme(color, darkTheme, muted) }
    }
    val useDynamic =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor && !LocalInspectionMode.current
    return remember(context, configuration, darkTheme, useDynamic) {
        if (useDynamic) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (darkTheme) DarkColorScheme else LightColorScheme
    }
}

internal fun hueColor(hue: Float): Color = Color.hsv(hue.coerceIn(0f, 360f), 0.8f, 0.85f)

internal fun generatedColorScheme(color: Color, darkTheme: Boolean, muted: Boolean = false): ColorScheme =
    dynamicColorScheme(
        seedColor = color,
        isDark = darkTheme,
        style = if (muted) PaletteStyle.Neutral else PaletteStyle.TonalSpot,
    )
