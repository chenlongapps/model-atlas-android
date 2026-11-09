package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModelBenchmarkCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allBundledBenchmarkVectorsLoadInCompose() {
        val resources = R.drawable::class.java.fields.filter { it.name.startsWith("ic_benchmark_") }
        assertEquals("Ten unique logos and one neutral fallback", 11, resources.size)
        compose.setContent {
            resources.forEach {
                val image = ImageVector.vectorResource(it.getInt(null))
                assertTrue(it.name, image.viewportWidth > 0f && image.viewportHeight > 0f && image.root.size > 0)
            }
        }
        compose.waitForIdle()
    }

    @Test fun everyLogoAndFallbackKeepArtworkAndWhiteBackgroundInBothThemes() {
        val id = mutableStateOf("artificial-analysis-intelligence-index-v5")
        val dark = mutableStateOf(false)
        compose.setContent { ModelAtlasTheme(darkTheme = dark.value, dynamicColor = false) {
            Surface { BenchmarkLogo(id.value) }
        } }
        listOf("artificial-analysis-intelligence-index-v5", "automationbench-v1.0.6", "chartography", "deepswe-v1.1",
            "gdpval-aa-v2.1", "humanitys-last-exam", "osworld-v2.1", "swe-bench-pro-v2", "terminal-bench-science-v0.1",
            "terminal-bench-v4", "vals-index-v2.2", "unknown").forEach { benchmarkId ->
            compose.runOnIdle { id.value = benchmarkId }
            val samples = listOf(false, true).map { isDark ->
                compose.runOnIdle { dark.value = isDark }
                val pixels = compose.onNodeWithTag("benchmark-logo:$benchmarkId").assertIsDisplayed().captureToImage().toPixelMap()
                assertEquals("$benchmarkId white base", Color.White, pixels[pixels.width / 2, 1])
                val artwork = buildList {
                    for (y in pixels.height / 8 until pixels.height * 7 / 8) {
                        for (x in pixels.width / 8 until pixels.width * 7 / 8) add(pixels[x, y])
                    }
                }
                assertTrue("$benchmarkId renders artwork", artwork.any { it != Color.White })
                artwork
            }
            assertEquals("$benchmarkId must not be tinted by the theme", samples[0], samples[1])
        }
    }

    @Test fun compactHeaderKeepsRawScoreExplicitUnitAndAccessibleConditions() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(400.dp)) { ModelBenchmarkCard(ModelBenchmark("GDPval-AA", null, scoreText = "899 ± 18",
                benchmarkId = "gdpval-aa-v2.1", unit = "Elo points", version = "2.1", reasoningEffort = "Adaptive/Max",
                variant = "without tools", toolsEnabled = false, description = "中文评测说明")) }
        } }
        compose.onNodeWithTag("benchmark-header:inline").assertIsDisplayed()
        compose.onNodeWithTag("benchmark-logo:gdpval-aa-v2.1").assertIsDisplayed()
        compose.onNodeWithContentDescription("GDPval-AA，分数：899 ± 18，Elo points").assertIsDisplayed()
        listOf("评测版本：2.1", "推理设置：Adaptive/Max", "评测配置／变体：without tools", "工具使用：未启用").forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithText("中文评测说明").assertIsDisplayed()
    }

    @Test fun missingScoreAndOptionalFieldsDoNotInventZeroUnitsOrConditions() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelBenchmarkCard(ModelBenchmark("Unknown", null, version = " ", description = " "))
        } }
        compose.onNodeWithText("暂无数据").assertIsDisplayed()
        listOf("0.0", "percent", "%", "越高越好", "评测版本", "推理设置", "工具使用").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("benchmark-logo:").assertIsDisplayed()
    }

    @Test fun numericScoreDisplaysAndAnnouncesExactlyOneDecimal() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelBenchmarkCard(ModelBenchmark("Example", 95.15678))
        } }
        compose.onNodeWithText("95.2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Example，分数：95.2").assertIsDisplayed()
        compose.onNodeWithText("95.15678").assertDoesNotExist()
    }

    @Test fun narrowDoubleFontRtlKeepsLongNameCompositeScoreAndConditionsUnclipped() {
        val name = "Long benchmark name with full configuration"
        val score = "95.12345 ± 0.00001 (without tools)"
        val variant = "Long configuration without tools or external resources"
        val description = "完整评测说明不截断，保留来源原文与中文说明。"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        ModelBenchmarkCard(ModelBenchmark(name, null, benchmarkId = "swe-bench-pro-v2", scoreText = score,
                            version = "2.0.0", variant = variant, description = description))
                    }
                }
            }
        }
        compose.onNodeWithTag("benchmark-header:stacked").assertExists()
        listOf(name, score, variant, description).forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse("$text height", layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue("$text start", layout.getLineLeft(line) >= -1f)
                assertTrue("$text end", layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse("$text ellipsis", layout.isLineEllipsized(line))
            }
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
    }
}
