package io.github.chenlongapps.modelatlas.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.github.chenlongapps.modelatlas.data.model.ThemeMode
import io.github.chenlongapps.modelatlas.data.model.ThemePreset
import io.github.chenlongapps.modelatlas.ui.theme.generatedColorScheme
import io.github.chenlongapps.modelatlas.ui.theme.hueColor
import org.junit.Assert.*
import org.junit.Test

class AppearanceColorTest {
    @Test fun modeResolutionIsIndependentOfColorSource() {
        assertTrue(ThemeMode.System.isDark(true))
        assertFalse(ThemeMode.System.isDark(false))
        assertFalse(ThemeMode.Light.isDark(true))
        assertTrue(ThemeMode.Dark.isDark(false))
        assertEquals(14, ThemePreset.entries.size)
    }

    @Test fun presetsAndEntireHueWheelHaveReadableForegroundPairsInBothModes() {
        for (dark in listOf(false, true)) {
            val inputs = ThemePreset.entries.map { Color(it.argb) to it.muted } +
                (0..360 step 10).map { hueColor(it.toFloat()) to false }
            for ((color, muted) in inputs) {
                val scheme = generatedColorScheme(color, dark, muted)
                val pairs = listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondary to scheme.onSecondary,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                    scheme.surface to scheme.onSurface,
                    scheme.surfaceContainerLowest to scheme.onSurface,
                    scheme.surfaceContainerLow to scheme.onSurfaceVariant,
                    scheme.errorContainer to scheme.onErrorContainer,
                )
                for ((background, foreground) in pairs) {
                    assertTrue("Unreadable pair for $color dark=$dark: $background / $foreground",
                        contrast(background, foreground) >= 4.5)
                }
                assertEquals(dark, scheme.surface.luminance() < 0.5f)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
