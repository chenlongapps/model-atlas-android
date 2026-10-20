package io.github.chenlongapps.modelatlas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    content: @Composable () -> Unit,
) {
    // 预览使用固定配色，避免预览宿主的壁纸改变浅色／深色设计稿。
    val context = LocalContext.current
    val colorScheme = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor && !LocalInspectionMode.current
    ) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColorScheme else LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
