package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProviderLogoRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allBundledProviderVectorsLoadInCompose() {
        val resources = R.drawable::class.java.fields.filter { it.name.startsWith("ic_provider_") }
        assertEquals("187 supplier vectors and one neutral fallback", 188, resources.size)
        compose.setContent {
            resources.forEach { resource ->
                val image = ImageVector.vectorResource(resource.getInt(null))
                assertTrue(resource.name, image.viewportWidth > 0f && image.viewportHeight > 0f)
                assertTrue("${resource.name} must not be empty", image.root.size > 0)
            }
        }
        compose.waitForIdle()
    }

    @Test fun originalArtworkAndWhiteBackgroundRemainVisibleInBothThemes() {
        val providers = listOf("302ai", "google", "openrouter", "unknown")
        val darkTheme = mutableStateOf(false)
        compose.setContent {
            ModelAtlasTheme(darkTheme = darkTheme.value, dynamicColor = false) {
                Surface {
                    Row {
                        providers.forEach { id ->
                            Box(Modifier.testTag("sample:$id")) { ProviderLogo(id) }
                        }
                    }
                }
            }
        }
        providers.forEach { id ->
            val samples = listOf(false, true).map { dark ->
                // 在同一物理位置切换主题，避免不同位置的子像素栅格化影响像素比较。
                compose.runOnIdle { darkTheme.value = dark }
                val node = compose.onNodeWithTag("sample:$id").assertIsDisplayed()
                val pixels = node.captureToImage().toPixelMap()
                assertEquals("$id background", Color.White, pixels[pixels.width / 2, 1])
                val artwork = buildList {
                    for (y in pixels.height / 10 until pixels.height * 9 / 10) {
                        for (x in pixels.width / 10 until pixels.width * 9 / 10) add(pixels[x, y])
                    }
                }
                assertTrue("$id must render artwork", artwork.any { it != Color.White })
                if (id == "302ai") assertTrue("Keep the source's explicit gray, not a theme tint", artwork.any {
                    kotlin.math.abs(it.red - 156f / 255f) < 0.01f &&
                        kotlin.math.abs(it.green - 155f / 255f) < 0.01f &&
                        kotlin.math.abs(it.blue - 155f / 255f) < 0.01f
                })
                artwork
            }
            assertEquals("$id pixels must not change with the theme", 0,
                samples[0].zip(samples[1]).count { (light, dark) -> light != dark })
        }
    }
}
