package io.github.chenlongapps.modelatlas.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class GlassSurfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun missingBackdropAndPreviewUseOpaqueClippedThemeColors() {
        var dark by mutableStateOf(false)
        var expected = Color.Unspecified
        compose.setContent {
            ModelAtlasTheme(darkTheme = dark, dynamicColor = false) {
                val surface = MaterialTheme.colorScheme.surfaceContainer
                SideEffect { expected = surface }
                Row(Modifier.background(Color.Magenta)) {
                    Box(Modifier.size(64.dp).testTag("no-source").glassSurface(null, CircleShape))
                    CompositionLocalProvider(LocalInspectionMode provides true) {
                        assertNull(rememberGlassBackdrop(Color.Black))
                        val backdrop = rememberCanvasBackdrop { error("Preview must not sample the backdrop") }
                        GlassSurface(
                            backdrop = backdrop,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp).testTag("preview"),
                            fallbackColor = surface.copy(alpha = 0.2f),
                        ) {}
                    }
                }
            }
        }

        for (darkTheme in listOf(false, true)) {
            compose.runOnIdle { dark = darkTheme }
            for (tag in listOf("no-source", "preview")) {
                val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                assertEquals(expected.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
                assertEquals(Color.Magenta.toArgb(), pixels[0, 0].toArgb())
            }
        }
    }

    @Test fun customShapeSamplesStableBackdropAndUpdatesTint() {
        val shape = GenericShape { size, _ ->
            moveTo(size.width / 2, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        var background by mutableStateOf(Color.Blue)
        var tintAlpha by mutableStateOf(0f)
        var source: Backdrop? = null
        compose.setContent {
            val backdrop = rememberGlassBackdrop(background)
            SideEffect { source = backdrop }
            Box(Modifier.size(64.dp).background(Color.Magenta).testTag("glass")) {
                Box(Modifier.matchParentSize().captureGlassBackdrop(backdrop))
                GlassSurface(
                    backdrop = backdrop,
                    shape = shape,
                    modifier = Modifier.matchParentSize(),
                    style = GlassStyle.Standard.copy(vibrancy = false, tintAlpha = tintAlpha),
                    tint = Color.Red,
                ) {}
            }
        }

        val original = compose.runOnIdle { source }
        assertNotNull(original)
        assertGlassPixels(Color.Blue)
        compose.runOnIdle { background = Color.Green }
        assertGlassPixels(Color.Green)
        assertSame(original, compose.runOnIdle { source })
        compose.runOnIdle { tintAlpha = 1f }
        assertGlassPixels(Color.Red)
    }

    private fun assertGlassPixels(expected: Color) {
        val pixels = compose.onNodeWithTag("glass").captureToImage().toPixelMap()
        assertEquals(expected.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
        assertEquals(Color.Magenta.toArgb(), pixels[0, 0].toArgb())
    }
}
