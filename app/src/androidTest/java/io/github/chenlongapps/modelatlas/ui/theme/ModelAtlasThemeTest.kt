package io.github.chenlongapps.modelatlas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelAtlasThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun fixedLightThemeRemainsAvailable() {
        assertPalette(dark = false, dynamic = false, expected = lightColorScheme())
    }

    @Test fun fixedDarkThemeRemainsAvailable() {
        assertPalette(dark = true, dynamic = false, expected = darkColorScheme())
    }

    @SdkSuppress(minSdkVersion = 31)
    @Test fun usesSystemDynamicLightPalette() {
        assertPalette(false, true, dynamicLightColorScheme(InstrumentationRegistry.getInstrumentation().targetContext))
    }

    @SdkSuppress(minSdkVersion = 31)
    @Test fun usesSystemDynamicDarkPalette() {
        assertPalette(true, true, dynamicDarkColorScheme(InstrumentationRegistry.getInstrumentation().targetContext))
    }

    @SdkSuppress(maxSdkVersion = 30)
    @Test fun olderAndroidFallsBackToFixedPalette() {
        assertPalette(dark = false, dynamic = true, expected = lightColorScheme())
    }

    private fun assertPalette(dark: Boolean, dynamic: Boolean, expected: ColorScheme) {
        lateinit var actual: ColorScheme
        compose.setContent {
            ModelAtlasTheme(darkTheme = dark, dynamicColor = dynamic) {
                val colors = MaterialTheme.colorScheme
                SideEffect { actual = colors }
            }
        }
        compose.runOnIdle {
            assertEquals(expected.primary, actual.primary)
            assertEquals(expected.primaryContainer, actual.primaryContainer)
            assertEquals(expected.secondaryContainer, actual.secondaryContainer)
            assertEquals(expected.tertiaryContainer, actual.tertiaryContainer)
            assertEquals(expected.surfaceContainerLowest, actual.surfaceContainerLowest)
            assertEquals(expected.surfaceContainerLow, actual.surfaceContainerLow)
            assertEquals(expected.onSurface, actual.onSurface)
        }
    }
}
