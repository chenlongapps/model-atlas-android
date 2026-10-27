package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.*
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun distinguishesLoadingMissingAndReadErrorWithLocalRetry() {
        var retries = 0
        var backs = 0
        val state = mutableStateOf(ModelDetailUiState("lab/a"))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelDetailScreen(state.value, onBack = { backs++ }, onRetry = { retries++ })
        } }
        compose.onNodeWithText("正在读取模型详情…").assertIsDisplayed()
        compose.onNodeWithContentDescription("模型详情").assertIsDisplayed()
        compose.onNodeWithTag("model-detail-collapsed-name").assertDoesNotExist()
        compose.runOnIdle { state.value = ModelDetailUiState("lab/a", ModelDetailStatus.ERROR) }
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = ModelDetailUiState("lab/a", ModelDetailStatus.NOT_FOUND)
        }
        compose.onNodeWithText("模型已不在当前目录中").assertIsDisplayed()
        compose.onNodeWithText("返回").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun overviewDisplaysOnlyExactParametersModalitiesWeightsAndFullLicense() {
        show(detail())
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        scrollTo("技术参数")
        compose.onNodeWithText("技术参数").assertIsDisplayed()
        compose.onNodeWithText("模型 ID").assertDoesNotExist()
        compose.onNodeWithText("lab/a", useUnmergedTree = true).assertDoesNotExist()
        scrollTo("131,072")
        compose.onNodeWithText("131,072").assertIsDisplayed()
        compose.onNodeWithContentDescription("上下文长度：131,072 tokens").assertIsDisplayed()
        scrollTo("Complete model license longer than sixteen characters")
        compose.onNodeWithText("Complete model license longer than sixteen characters").assertIsDisplayed()
        scrollTo("参数量")
        compose.onAllNodes(hasText("参数量")).assertCountEquals(1)
        compose.onNode(hasText("参数量") and hasText("暂无数据") and
            hasAnyAncestor(hasTestTag("overview-section:parameters"))).assertIsDisplayed()
        compose.onNode(hasText("参数量") and
            hasAnyAncestor(hasTestTag("overview-section:weights"))).assertDoesNotExist()
        scrollTo("模态")
        compose.onNode(hasText("输入") and hasText("文本") and hasText("future") and
            hasAnyAncestor(hasTestTag("overview-section:modalities"))).assertIsDisplayed()
        compose.onNode(hasText("输出") and hasText("无") and
            hasAnyAncestor(hasTestTag("overview-section:modalities"))).assertIsDisplayed()
        listOf("模型权重", "权重地址", "https://example.org/weights", "打开：模型权重（外部链接）").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithText("开放权重不等于开源或允许商用，请核对模型许可与使用条款。").assertDoesNotExist()
        listOf("模型能力", "模型系列", "相关资料", "数据来源与时效", "权重格式", "量化", "safetensors", "FP8").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        selectTab("PROVIDERS")
        scrollTo("暂无显式关联的 API 服务。")
        compose.onNodeWithText("暂无显式关联的 API 服务。").assertIsDisplayed()
    }

    @Test fun overviewShowsAlignedInputAndOutputRowsBetweenParametersAndWeights() {
        show(detail().copy(metadata = detail().metadata.copy(
            inputModalities = listOf("text", "image", "video"), outputModalities = listOf("text"),
        )))
        scrollTo("模态")
        val input = compose.onNodeWithContentDescription("输入模态：文本、图片、视频")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val output = compose.onNodeWithContentDescription("输出模态：文本")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val parameters = compose.onNodeWithText("技术参数").getUnclippedBoundsInRoot()
        val modalities = compose.onNodeWithText("模态").getUnclippedBoundsInRoot()
        val weights = compose.onNodeWithText("权重与许可").getUnclippedBoundsInRoot()
        assertTrue(parameters.bottom <= modalities.top)
        assertTrue(modalities.bottom <= input.top)
        assertTrue("Input must be above output", input.bottom < output.top)
        assertEquals(input.left.value, output.left.value, 1f)
        assertEquals(input.right.value, output.right.value, 1f)
        assertTrue(output.bottom <= weights.top)
        compose.onNodeWithTag("overview-modalities-direction", useUnmergedTree = true).assertDoesNotExist()
        val valueBounds = listOf("input", "output").map { direction ->
            val label = compose.onNodeWithText(if (direction == "input") "输入" else "输出", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
            val value = compose.onNode(hasText("文本") and hasAnyAncestor(hasTestTag("overview-$direction-modalities")),
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("The label must precede its value", label.right < value.left)
            assertEquals("Label and value must share the first line", label.top.value, value.top.value, 1f)
            value
        }
        assertEquals("Values must share the same starting column", valueBounds[0].left.value, valueBounds[1].left.value, 1f)
        listOf("模态", "输入", "图片", "视频", "输出").forEach(::assertTextNotClipped)
        compose.onAllNodes(hasText("文本") and hasAnyAncestor(hasTestTag("overview-input-modalities")),
            useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun narrowModalityRowsWrapInsideTheValueColumn() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(metadata = detail().metadata.copy(
                inputModalities = listOf("text", "image", "audio", "video", "future-modality"),
                outputModalities = listOf("text"),
            ))), {}) }
        } }
        scrollTo("模态")
        val input = hasAnyAncestor(hasTestTag("overview-input-modalities"))
        val first = compose.onNode(hasText("文本") and input, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val wrapped = compose.onNode(hasText("视频") and input, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val label = compose.onNodeWithText("输入", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Many modalities must wrap", wrapped.top > first.top)
        assertTrue("Wrapped values must stay outside the label column", wrapped.left > label.right)
        listOf("输入", "图片", "音频", "视频", "future-modality", "输出").forEach(::assertTextNotClipped)
        compose.onNodeWithContentDescription("输入模态：文本、图片、音频、视频、future-modality").assertExists()
    }

    @Test fun overviewDistinguishesMissingEmptyAndUnknownModalitiesWithoutServiceFallback() {
        val offering = ModelDetailOffering("service", "Service", ModelDetailMetadata(
            inputModalities = listOf("image"), outputModalities = listOf("audio")), null,
            null, null, null, null, "models.dev", 100)
        val model = detail().copy(metadata = detail().metadata.copy(
            inputModalities = null, outputModalities = emptyList(),
        ), providers = listOf(ModelDetailProvider("p", "Provider", null, listOf(offering))))
        val state = mutableStateOf(content(model))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        scrollTo("模态")
        compose.onNodeWithContentDescription("输入模态：暂无数据").assertIsDisplayed()
        compose.onNodeWithContentDescription("输出模态：无").assertIsDisplayed()
        compose.runOnIdle { state.value = content(model.copy(metadata = model.metadata.copy(
            inputModalities = emptyList(), outputModalities = null,
        ))) }
        compose.onNodeWithContentDescription("输入模态：无").assertIsDisplayed()
        compose.onNodeWithContentDescription("输出模态：暂无数据").assertIsDisplayed()
        compose.runOnIdle { state.value = content(model.copy(metadata = model.metadata.copy(
            inputModalities = listOf("text", "future"), outputModalities = listOf("video", "audio"),
        ))) }
        compose.onNodeWithContentDescription("输入模态：文本、future").assertIsDisplayed()
        compose.onNodeWithContentDescription("输出模态：视频、音频").assertIsDisplayed()
        compose.onNodeWithContentDescription("输出模态：文本").assertDoesNotExist()
    }

    @Test fun overviewEmphasizesTwoMetricsWithoutNestedCardsAndKeepsCompleteLicense() {
        val license = "Complete model license longer than sixteen characters"
        var surface = Color.Unspecified
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            surface = MaterialTheme.colorScheme.surface
            Box(Modifier.width(400.dp)) { ModelDetailScreen(content(detail().copy(metadata = detail().metadata.copy(
                releaseDate = "2026-09-15", knowledgeCutoff = "2026-06", contextLimitTokens = 1_000_000,
                outputLimitTokens = 384_000, inputLimitTokens = 920_000,
            ))), {}) }
        } }
        scrollTo("1,000,000")
        val metrics = listOf("context", "output", "input", "parameters").map {
            compose.onNodeWithTag("overview-metric:$it").getUnclippedBoundsInRoot()
        }
        assertTrue(metrics[0].right < metrics[1].left)
        assertEquals(metrics[0].top.value, metrics[1].top.value, 1f)
        assertEquals(metrics[0].bottom.value, metrics[1].bottom.value, 1f)
        assertTrue(metrics[0].bottom <= metrics[2].top)
        assertEquals(metrics[0].left, metrics[2].left)
        assertEquals(metrics[2].top.value, metrics[3].top.value, 1f)
        listOf("context", "output", "input", "parameters").forEach { tag ->
            val pixels = compose.onNodeWithTag("overview-metric:$tag").captureToImage().toPixelMap()
            assertEquals("Metrics must share the outer card background", surface.toArgb(), pixels[0, 0].toArgb())
        }
        listOf("1,000,000", "384,000").forEach(::assertTextNotClipped)
        compose.onNodeWithContentDescription("上下文长度：1,000,000 tokens").assertIsDisplayed()
        compose.onNodeWithContentDescription("最大输出：384,000 tokens").assertIsDisplayed()
        val valueBounds = compose.onNodeWithText("1,000,000", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val unitBounds = compose.onNode(hasText("tokens") and hasAnyAncestor(hasTestTag("overview-metric:context")),
            useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("The token unit belongs below the exact value", valueBounds.bottom <= unitBounds.top)
        listOf("发布时间" to "2026-09-15", "知识截止时间" to "2026-06").forEach { (label, value) ->
            scrollTo(value)
            val labelBounds = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val valueBounds = compose.onNodeWithText(value, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Dates belong below their labels in the quiet footer", labelBounds.bottom <= valueBounds.top)
            assertEquals(labelBounds.left, valueBounds.left, 1f)
            assertTextNotClipped(value)
        }
        val primaryLayout = textLayout("1,000,000")
        val secondaryLayout = textLayout("920,000 tokens")
        assertTrue("Primary metrics must have a stronger typographic hierarchy",
            primaryLayout.layoutInput.style.fontSize > secondaryLayout.layoutInput.style.fontSize)
        compose.onNodeWithContentDescription("最大输入：920,000 tokens").assertExists()
        compose.onNodeWithText("2026-06-01").assertDoesNotExist()
        scrollTo(license)
        val label = compose.onNodeWithText("模型许可", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText(license, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Long license must use a separate full-width line", value.top >= label.bottom)
        assertEquals(label.left, value.left, 1f)
        assertTextNotClipped(license)
    }

    @Test fun darkNarrowLargeFontRtlOverviewStacksFieldsAndKeepsCompleteValues() {
        val license = "Complete model license longer than sixteen characters"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(metadata = detail().metadata.copy(
                        releaseDate = "2026-09-15", knowledgeCutoff = "2026-06", outputLimitTokens = 32_000,
                        inputModalities = listOf("text", "image", "audio", "video", "future-modality"),
                        outputModalities = listOf("text"),
                    ))), {}) }
                }
            }
        }
        scrollTo("131,072")
        val label = compose.onNodeWithText("上下文长度", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("131,072", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Large-font fields must stack", label.bottom <= value.top)
        val metrics = listOf("context", "output", "input", "parameters").map {
            compose.onNodeWithTag("overview-metric:$it").getUnclippedBoundsInRoot()
        }
        metrics.zipWithNext().forEach { (first, second) ->
            assertTrue("Large-font metric cards must form a single column", first.bottom <= second.top)
            assertEquals(first.right.value, second.right.value, 1f)
        }
        listOf("发布时间", "2026-09-15", "知识截止时间", "2026-06", "131,072",
            "32,000", license, "参数量", "模态", "输入", "图片", "音频", "视频", "future-modality", "输出").forEach {
            scrollTo(it)
            assertTextNotClipped(it)
        }
        scrollTo("输入")
        val input = compose.onNodeWithTag("overview-input-modalities").getUnclippedBoundsInRoot()
        val output = compose.onNodeWithTag("overview-output-modalities").getUnclippedBoundsInRoot()
        assertTrue("Modality panels must stack at large font", input.bottom <= output.top)
        compose.onNodeWithTag("overview-modalities-direction", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun narrowOverviewKeepsVeryLargeExactValuesAndMissingLimitsDistinctFromZero() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(metadata = detail().metadata.copy(
                contextLimitTokens = Long.MAX_VALUE, outputLimitTokens = 0, inputLimitTokens = null,
            ))), {}) }
        } }
        val exact = "9,223,372,036,854,775,807"
        scrollTo(exact)
        assertTextNotClipped(exact)
        compose.onNodeWithContentDescription("上下文长度：$exact tokens").assertIsDisplayed()
        val context = compose.onNodeWithTag("overview-metric:context").getUnclippedBoundsInRoot()
        val output = compose.onNodeWithTag("overview-metric:output").getUnclippedBoundsInRoot()
        assertTrue("Long exact values force a single column", context.bottom <= output.top)
        scrollTo("0")
        compose.onNodeWithContentDescription("最大输出：0 tokens").assertIsDisplayed()
        scrollTo("最大输入")
        compose.onNodeWithContentDescription("最大输入：暂无数据").assertIsDisplayed()
        compose.onNode(hasText("tokens") and hasAnyAncestor(hasTestTag("overview-metric:input")),
            useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("9.2M").assertDoesNotExist()
    }

    @Test fun overviewWeightsKeepAllThreeStatesAndFullLicenseWithoutAddingActions() {
        val model = detail().copy(license = "MIT", metadata = detail().metadata.copy(openWeights = true))
        val state = mutableStateOf(content(model))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        scrollTo("权重与许可")
        compose.onNodeWithContentDescription("权重公开状态：开放权重").assertIsDisplayed()
        compose.onNodeWithText("权重公开状态", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { state.value = content(model.copy(metadata = model.metadata.copy(openWeights = false))) }
        compose.onNodeWithContentDescription("权重公开状态：权重未公开").assertIsDisplayed()
        compose.runOnIdle { state.value = content(model.copy(license = null, metadata = model.metadata.copy(openWeights = null))) }
        compose.onNodeWithContentDescription("权重公开状态：暂无数据").assertIsDisplayed()
        compose.onNode(hasText("开放权重：暂无数据") and hasAnyAncestor(hasTestTag("overview-section:weights")))
            .assertIsDisplayed()
        scrollTo("模型许可")
        compose.onNode(hasText("模型许可") and hasText("暂无数据")).assertIsDisplayed()
        compose.onNode(hasTestTag("overview-weights-status") and hasClickAction()).assertDoesNotExist()
        compose.onNode(hasText("模型许可") and hasClickAction()).assertDoesNotExist()
    }

    @Test fun compactServiceCardUsesInlineIdentityAndFourAlignedMetrics() {
        val offering = ModelDetailOffering("example-chat-v1", "Hidden service name", ModelDetailMetadata(
            contextLimitTokens = 65_536, outputLimitTokens = 16_384), null,
            ModelDetailPrice(input = 0.6, output = 2.4), null, null, null, "models.dev", 100)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(400.dp)) { ModelDetailScreen(content(detail().copy(providers = listOf(
                ModelDetailProvider("p", "Example API", null, listOf(offering)),
            ))), {}) }
        } }
        selectTab("PROVIDERS")
        scrollTo("Example API")
        val provider = compose.onNodeWithText("Example API", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("provider-logo:p", useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Logo must precede the provider name", logo.right < provider.left)
        compose.onNodeWithTag("provider-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        val serviceId = compose.onNodeWithText("example-chat-v1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Provider must be above the service identity", provider.bottom <= serviceId.top)
        assertEquals("Service ID must align with the provider name", provider.left, serviceId.left, 1f)
        val values = listOf("65,536", "16,384", "$0.60", "$2.40")
        scrollTo("$2.40")
        val bounds = values.map { compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (first, second) ->
            assertTrue("All four metrics must occupy separate columns", first.right < second.left)
            assertEquals("All four metrics must align vertically", first.top, second.top, 1f)
        }
        values.forEach(::assertTextNotClipped)
        compose.onNodeWithText("65,536 tokens").assertDoesNotExist()
        compose.onNodeWithText("16,384 tokens").assertDoesNotExist()
        scrollTo("美元 / 1M tokens")
        compose.onAllNodes(hasText("美元 / 1M tokens")).assertCountEquals(1)
        compose.onNodeWithText("美元 / 1M tokens").assertIsDisplayed()
        compose.onNodeWithContentDescription("模型服务 ID：example-chat-v1").assertIsDisplayed()
        compose.onNodeWithText("服务商").assertDoesNotExist()
        compose.onNodeWithText("Hidden service name").assertDoesNotExist()
    }

    @Test fun compactServiceCardFallsBackToTwoColumnsWithoutRoundingValues() {
        val offering = ModelDetailOffering("complete-service-id", "Hidden", ModelDetailMetadata(
            contextLimitTokens = 1_000_000, outputLimitTokens = 384_000), null,
            ModelDetailPrice(input = 0.0000123, output = 0.0), null, null, null, "models.dev", 100)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(providers = listOf(
                ModelDetailProvider("google", "Google Cloud", null, listOf(offering)),
            ))), {}) }
        } }
        selectTab("PROVIDERS")
        scrollTo("$0.00")
        val values = listOf("1,000,000", "384,000", "$0.0000123", "$0.00")
        val bounds = values.map { compose.onNodeWithText(it, useUnmergedTree = true).getUnclippedBoundsInRoot() }
        assertTrue(bounds[0].right < bounds[1].left)
        assertEquals(bounds[0].top.value, bounds[1].top.value, 1f)
        assertTrue("Prices must start below the limits", bounds[0].bottom <= bounds[2].top)
        assertTrue(bounds[2].right < bounds[3].left)
        assertEquals(bounds[2].top.value, bounds[3].top.value, 1f)
        values.forEach { scrollTo(it); assertTextNotClipped(it) }
    }

    @Test fun priceLogosUseProviderIdsNotModelLabOrProviderDisplayNameAndRepeatPerService() {
        val offering = ModelDetailOffering("service-a", "Hidden", ModelDetailMetadata(), null,
            ModelDetailPrice(input = 0.6), null, null, null, "models.dev", 100)
        show(detail().copy(id = "deepseek/example", providers = listOf(
            ModelDetailProvider("openrouter", "OpenAI", null, listOf(offering, offering.copy(id = "service-b"))),
            ModelDetailProvider("not-collected", "Google", null, listOf(offering.copy(id = "service-c"))),
        )))
        selectTab("PROVIDERS")
        listOf("service-a", "service-b").forEach { service ->
            scrollTo(service)
            val ancestor = hasAnyAncestor(hasTestTag("model-service:10:openrouter$service"))
            compose.onNode(hasTestTag("provider-logo:openrouter") and ancestor, useUnmergedTree = true)
                .assertIsDisplayed()
            compose.onNode(hasTestTag("provider-logo") and ancestor, useUnmergedTree = true).assertIsDisplayed()
            compose.onNode(hasText("OpenAI") and ancestor, useUnmergedTree = true).assertIsDisplayed()
        }
        scrollTo("service-c")
        compose.onNodeWithTag("provider-logo:not-collected", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("provider-logo-fallback", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("provider-logo:deepseek", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("provider-logo:openai", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("provider-logo:google", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun centeredHeroPrecedesAllThreeTabsWithoutDuplicatedMetrics() {
        val model = detail().copy(
            id = "deepseek/example", name = "DeepSeek Example", license = "MIT",
            metadata = detail().metadata.copy(
                description = "来自规范模型的描述", releaseDate = "2026-10",
                outputLimitTokens = 384_000, openWeights = true,
            ),
        )
        show(model)
        scrollToTabs()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("model-detail-tabs"))).assertCountEquals(3)
        listOf("OVERVIEW", "PROVIDERS", "BENCHMARKS").forEach { tab ->
            selectTab(tab)
            compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(0)
            compose.onNodeWithText("DeepSeek Example").assertIsDisplayed()
            compose.onNodeWithText("DeepSeek").assertIsDisplayed()
            compose.onNodeWithText("开放权重 · MIT").assertIsDisplayed()
            compose.onNodeWithText("来自规范模型的描述").assertIsDisplayed()
            compose.onNodeWithText("2026-10-01").assertDoesNotExist()
            val sharedHeader = hasAnyAncestor(hasTestTag("model-detail-summary"))
            val logo = compose.onNode(hasTestTag("lab-logo") and sharedHeader, useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val name = compose.onNodeWithTag("model-name").fetchSemanticsNode().boundsInRoot
            val lab = compose.onNodeWithTag("model-lab").fetchSemanticsNode().boundsInRoot
            assertEquals(logo.center.x, name.center.x, 1f)
            assertTrue(logo.bottom <= name.top)
            assertTrue(name.bottom <= lab.top)
            scrollToTabs()
            val summary = compose.onNodeWithTag("model-detail-summary").fetchSemanticsNode().boundsInRoot
            val tabs = compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot
            // 在同一滚动位置检查顺序，不依赖设备视口高度。
            assertTrue(summary.bottom <= tabs.top)
            compose.onNodeWithTag("model-detail-metrics").assertDoesNotExist()
            listOf("2026-10", "131.1K", "384K").forEach {
                compose.onNode(hasText(it) and sharedHeader, useUnmergedTree = true).assertDoesNotExist()
            }
            compose.onNode(hasText("future") and sharedHeader and hasAnyAncestor(hasTestTag("input-modalities")), useUnmergedTree = true)
                .assertExists()
            compose.onNode(hasText("无") and sharedHeader and hasAnyAncestor(hasTestTag("output-modalities")), useUnmergedTree = true)
                .assertExists()
        }
    }

    @Test fun summaryKeepsUnknownWeightsAndModalitiesDistinctFromExplicitEmpty() {
        show(detail().copy(metadata = ModelDetailMetadata(
            releaseDate = "2026", contextLimitTokens = 0,
            inputModalities = null, outputModalities = emptyList(),
        )))
        compose.onNodeWithText("暂无模型描述").assertIsDisplayed()
        compose.onNode(hasText("开放权重：暂无数据") and hasAnyAncestor(hasTestTag("model-detail-summary")))
            .assertIsDisplayed()
        compose.onNode(hasText("暂无数据") and hasAnyAncestor(hasTestTag("input-modalities")), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNode(hasText("无") and hasAnyAncestor(hasTestTag("output-modalities")), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("model-detail-metrics").assertDoesNotExist()
        scrollTo("0")
        compose.onNodeWithText("0").assertIsDisplayed()
        compose.onNodeWithContentDescription("上下文长度：0 tokens").assertIsDisplayed()
        scrollTo("2026")
        compose.onNodeWithText("2026").assertIsDisplayed()
    }

    @Test fun lightHeaderUsesPageBackgroundAndKeepsInsetsWithoutMetrics() {
        assertHeaderAndInsets(darkTheme = false)
    }

    @Test fun darkHeaderUsesPageBackgroundAndKeepsInsetsWithoutMetrics() {
        assertHeaderAndInsets(darkTheme = true)
    }

    @Test fun heroBadgesOnlyReportExplicitSupportAndDoNotInferLongContext() {
        show(detail().copy(metadata = detail().metadata.copy(contextLimitTokens = 1_000_000)))
        val badges = hasAnyAncestor(hasTestTag("model-detail-capability-badges"))
        compose.onNode(hasText("文件附件") and badges, useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText("推理") and badges, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("工具调用") and badges, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("长上下文").assertDoesNotExist()
    }

    @Test fun overviewRestoresExactValuesAndItsOwnScrollThroughAsyncLoading() {
        val restoration = StateRestorationTester(compose)
        val model = detail()
        val state = mutableStateOf(content(model))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        scrollTo("131,072")
        val before = compose.onNodeWithText("131,072").fetchSemanticsNode().boundsInRoot.top
        // 恢复概览内的精确参数阅读位置时，也应保留当时的标题状态。
        val compactTitleBefore = compose.onNodeWithContentDescription(model.name).isDisplayed()
        compose.runOnIdle { state.value = ModelDetailUiState(model.id) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = content(model) }
        compose.onNodeWithText("131,072").assertIsDisplayed()
        assertEquals(compactTitleBefore, compose.onNodeWithContentDescription(model.name).isDisplayed())
        assertEquals(before, compose.onNodeWithText("131,072").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        val tabsBefore = compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("detail-tab:BENCHMARKS").performClick()
        scrollTo("暂无评测数据。")
        compose.onNodeWithText("暂无评测数据。").assertIsDisplayed()
        selectTab("OVERVIEW")
        assertEquals(tabsBefore, compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollTo("131,072")
        compose.onNodeWithText("131,072").assertIsDisplayed()
    }

    @Test fun showsEveryServiceWithOnlyItsOwnLimitsAndBasePrices() {
        val restoration = StateRestorationTester(compose)
        val metadata = ModelDetailMetadata(contextLimitTokens = 1024, outputLimitTokens = 256)
        val offering = ModelDetailOffering(
            "service-a", "Service A", metadata, "beta",
            ModelDetailPrice(input = 0.0, output = 0.003, tiers = listOf(
                ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 1.0, reasoning = 0.004)),
            )), null, null, null, "models.dev", 100,
        )
        val model = detail().copy(providers = listOf(ModelDetailProvider("p", "Provider", null,
            listOf(offering, offering.copy(id = "service-b", name = "Service B", price = null,
                metadata = ModelDetailMetadata())))))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model), {}) } }
        selectTab("PROVIDERS")
        scrollTo("service-a")
        scrollTo("$0.003")
        compose.onNodeWithText("$0.00").assertIsDisplayed()
        compose.onNodeWithText("$0.003").assertIsDisplayed()
        scrollTo("1,024")
        compose.onNodeWithText("1,024").assertIsDisplayed()
        scrollTo("256")
        compose.onNodeWithText("256").assertIsDisplayed()
        listOf("Service A", "beta", "展开档位价格", "展开服务参数与来源", "$0.004", "272,000 tokens").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        scrollTo("service-b")
        compose.onNodeWithText("service-b").assertIsDisplayed()
        compose.onNode(hasText("输入") and hasText("暂无数据")).assertIsDisplayed()
        compose.onNode(hasText("输出") and hasText("暂无数据")).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("service-b").assertIsDisplayed()
    }

    @Test fun invalidAndUnavailableExternalLinksNeverCrash() {
        var calls = 0
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { calls++; throw IllegalArgumentException("no browser") }
            }) {
                ModelAtlasTheme(dynamicColor = false) {
                    Column {
                        DetailLink("Unsafe", "javascript:alert(1)")
                        DetailLink("Safe", "https://example.org")
                    }
                }
            }
        }
        compose.onNodeWithText("打开：Unsafe（外部链接）").assertIsNotEnabled()
        compose.onNodeWithText("打开：Safe（外部链接）").performClick()
        compose.onNodeWithText("无法打开链接，请确认设备上有可用浏览器。").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun repeatsProviderPerServiceWithoutMixingModesOrFillingMissingLimits() {
        val offering = ModelDetailOffering("service", "First service", ModelDetailMetadata(), null,
            ModelDetailPrice(input = 0.0), mapOf("fast" to ModelDetailPrice(input = 0.75)),
            null, null, "models.dev", 100)
        show(detail().copy(providers = listOf(
            ModelDetailProvider("p1", "First provider", null, listOf(offering)),
            ModelDetailProvider("p2", "Second provider", null,
                listOf(offering.copy(name = "Second service", price = null, modes = null))),
        )))
        selectTab("PROVIDERS")
        scrollTo("First provider")
        compose.onNodeWithText("First provider").assertIsDisplayed()
        val firstService = hasAnyAncestor(hasTestTag("model-service:2:p1service"))
        compose.onNode(hasText("上下文长度") and hasText("暂无数据") and firstService).assertIsDisplayed()
        compose.onNode(hasText("最大输出") and hasText("暂无数据") and firstService).assertIsDisplayed()
        compose.onNodeWithText("独立模式：fast").assertDoesNotExist()
        compose.onNodeWithText("$0.75").assertDoesNotExist()
        compose.onNodeWithText("131,072").assertDoesNotExist()
        scrollTo("Second provider")
        compose.onNodeWithText("Second provider").assertIsDisplayed()
        compose.onNodeWithText("Second service").assertDoesNotExist()
        compose.onNode(hasContentDescription("模型服务 ID：service") and hasText("service") and
            hasAnyAncestor(hasTestTag("model-service:2:p2service"))).assertIsDisplayed()
    }

    @Test fun restoringThroughAsyncLoadingDoesNotClampTheSavedContentPosition() {
        val restoration = StateRestorationTester(compose)
        val model = detail()
        val state = mutableStateOf(content(model))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        scrollTo("参数量")
        val before = compose.onNodeWithText("参数量").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = ModelDetailUiState(model.id) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("正在读取模型详情…").assertIsDisplayed()
        compose.runOnIdle { state.value = content(model) }
        compose.onNodeWithText("参数量").assertIsDisplayed()
        compose.onNodeWithContentDescription(model.name).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("参数量").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun tierAndModeOnlyPricesDoNotBecomeBasePrices() {
        val offering = ModelDetailOffering("tier-only", "Hidden service name", ModelDetailMetadata(), "deprecated",
            ModelDetailPrice(tiers = listOf(ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 17.0)))),
            mapOf("fast" to ModelDetailPrice(input = 19.0, output = 23.0)), null, null, "models.dev", 100)
        show(detail().copy(providers = listOf(ModelDetailProvider("hidden-provider-id", "Visible provider",
            "https://example.org/hidden-docs", listOf(offering)))))
        selectTab("PROVIDERS")
        scrollTo("tier-only")
        listOf("Visible provider", "tier-only", "上下文长度", "最大输出", "输入", "输出").forEach {
            scrollTo(it)
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("模型服务 ID：tier-only").assertIsDisplayed()
        compose.onNode(hasText("输入") and hasText("暂无数据")).assertIsDisplayed()
        compose.onNode(hasText("输出") and hasText("暂无数据")).assertIsDisplayed()
        listOf("$17.00", "$19.00", "$23.00", "deprecated", "Hidden service name", "hidden-provider-id",
            "https://example.org/hidden-docs", "272,000 tokens").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test fun darkNarrowLargeFontRtlServiceFieldsAndHighPrecisionPricesDoNotClip() {
        val provider = "Provider with a complete long name"
        val service = "long-service-identifier-without-truncation"
        val offering = ModelDetailOffering(service, "Hidden", ModelDetailMetadata(
            contextLimitTokens = 65_536, outputLimitTokens = 16_384), null,
            ModelDetailPrice(input = 0.0000123, output = 0.0), null, null, null, "models.dev", 100)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(
                        providers = listOf(ModelDetailProvider("openrouter", provider, null, listOf(offering))),
                    )), {}) }
                }
            }
        }
        selectTab("PROVIDERS")
        scrollTo(provider)
        val logo = compose.onNodeWithTag("provider-logo:openrouter", useUnmergedTree = true).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val providerBounds = compose.onNodeWithText(provider, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Logo must stay on the leading side in RTL", logo.left >= providerBounds.right)
        listOf("上下文长度", "最大输出", "输入", "输出",
            provider, service, "65,536", "16,384", "$0.0000123", "$0.00").forEach {
            scrollTo(it)
            assertTextNotClipped(it)
        }
        val ancestor = hasAnyAncestor(hasTestTag("detail-service-metrics"))
        listOf("65,536", "16,384", "$0.0000123", "$0.00").zipWithNext().forEach { (first, second) ->
            // 用未裁剪布局边界检查整组方向，不要求大字号时四项同时处于视口。
            val firstBounds = compose.onNode(hasText(first) and ancestor, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val secondBounds = compose.onNode(hasText(second) and ancestor, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("High-precision metrics must stack at large font", firstBounds.bottom <= secondBounds.top)
        }
    }

    @Test fun headerBecomesModelNameAndStaysReachableWhileTabsScrollAway() {
        var backs = 0
        val model = detail().copy(benchmarks = List(12) { ModelBenchmark("Evaluation $it", it.toDouble()) })
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelDetailScreen(content(model), { backs++ })
        } }
        val header = compose.onNodeWithTag("model-detail-header")
        val initialBounds = header.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("模型详情").assertIsDisplayed()
        compose.onNodeWithContentDescription(model.name).assertDoesNotExist()
        scrollToTabs()
        compose.onNodeWithTag("model-detail-tabs").assertIsDisplayed()
        selectTab("BENCHMARKS")
        scrollTo("Evaluation 11")
        header.assertIsDisplayed()
        assertEquals(initialBounds, header.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription(model.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("模型详情").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
        compose.onNodeWithTag("model-detail-tabs").assertIsNotDisplayed()
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(0)
        compose.onNodeWithContentDescription("模型详情").assertIsDisplayed()
        compose.onNodeWithContentDescription(model.name).assertDoesNotExist()
        compose.onNodeWithText(model.name).assertIsDisplayed()
        scrollToTabs()
        compose.onNodeWithTag("model-detail-tabs").assertIsDisplayed()
    }

    @Test fun titleCrossfadesAtIntermediateScrollAndReversesAtTheSamePosition() {
        show(detail())
        val header = compose.onNodeWithTag("model-detail-header")
        val list = compose.onNodeWithTag("model-detail-scroll")
        val initialHeaderBounds = header.fetchSemanticsNode().boundsInRoot
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        val nameBounds = compose.onNodeWithTag("model-name").fetchSemanticsNode().boundsInRoot
        val expanded = header.captureToImage()
        // 大标题到达视口顶边时截取交接的中间帧，再向前／反向滚动同一距离。
        val distance = nameBounds.top - listBounds.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, distance) }
        val middle = header.captureToImage()
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, nameBounds.height) }
        val collapsed = header.captureToImage()
        compose.onNodeWithContentDescription("Model A").assertIsDisplayed()
        assertTrue("The intermediate title must differ from both endpoints", imageDiffers(middle, expanded) && imageDiffers(middle, collapsed))
        assertEquals(initialHeaderBounds, header.fetchSemanticsNode().boundsInRoot)
        assertEquals(listBounds, list.fetchSemanticsNode().boundsInRoot)
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -nameBounds.height) }
        assertFalse("Returning to the same scroll position restores the same title", imageDiffers(middle, header.captureToImage()))
        list.performScrollToIndex(0)
        assertFalse(imageDiffers(expanded, header.captureToImage()))
    }

    @Test fun benchmarkTabShowsRawScoresMetadataAndSafeSourceWithoutInventingUnits() {
        show(detail().copy(benchmarks = listOf(
            ModelBenchmark("Example evaluation", 0.003, "accuracy", "https://example.org/eval",
                "2026-10", "v2", "no tools", "public", "test-harness"),
            ModelBenchmark("Zero evaluation", 0.0, source = "javascript:alert(1)"),
        )))
        selectTab("BENCHMARKS")
        scrollTo("打开：评测来源（外部链接）")
        compose.onNodeWithText("打开：评测来源（外部链接）").assertIsDisplayed()
        listOf("Example evaluation", "0.003", "accuracy", "https://example.org/eval", "2026-10", "v2",
            "no tools", "public", "test-harness", "Zero evaluation", "0.0", "javascript:alert(1)").forEach {
            scrollTo(it)
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNode(hasText("javascript:alert(1)") and hasClickAction()).assertDoesNotExist()
    }

    @Test fun missingAndExplicitlyEmptyBenchmarksHaveDifferentEmptyStates() {
        val state = mutableStateOf(content(detail()))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        selectTab("BENCHMARKS")
        scrollTo("暂无评测数据。")
        compose.onNodeWithText("暂无评测数据。").assertIsDisplayed()
        compose.runOnIdle { state.value = content(detail().copy(benchmarks = emptyList())) }
        scrollTo("来源未列出评测记录。")
        compose.onNodeWithText("来源未列出评测记录。").assertIsDisplayed()
        selectTab("PROVIDERS")
        scrollTo("暂无显式关联的 API 服务。")
        compose.onNodeWithText("暂无显式关联的 API 服务。").assertIsDisplayed()
    }

    @Test fun switchingTabsPreservesServicePositionAndSelectionThroughAsyncRestoration() {
        val restoration = StateRestorationTester(compose)
        val offering = ModelDetailOffering("s", "Service", ModelDetailMetadata(), null,
            ModelDetailPrice(input = 0.0, tiers = listOf(
                ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 0.003)),
            )), null, null, null, "models.dev", 100)
        val model = detail().copy(providers = listOf(ModelDetailProvider("p", "Provider", null,
            List(12) { offering.copy(id = "service-$it") })))
        val state = mutableStateOf(content(model))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        selectTab("PROVIDERS")
        scrollTo("service-8")
        // 标签也随列表滚动，先滚回标签所在条目再切换。
        scrollToTabs()
        val before = compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("detail-tab:OVERVIEW").performClick()
        selectTab("BENCHMARKS")
        selectTab("PROVIDERS")
        assertEquals(before, compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollTo("service-8")
        val contentBefore = compose.onNodeWithText("service-8").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = ModelDetailUiState(model.id) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = content(model) }
        compose.onNodeWithText("service-8").assertIsDisplayed()
        assertEquals(contentBefore, compose.onNodeWithText("service-8").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
    }

    @Test fun swipingSectionsPreservesDeepServiceScrollAndRestoresSelectedPage() {
        val restoration = StateRestorationTester(compose)
        val offering = ModelDetailOffering("s", "Service", ModelDetailMetadata(), null,
            ModelDetailPrice(input = 0.0, tiers = listOf(
                ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 0.003)),
            )), null, null, null, "models.dev", 100)
        val model = detail().copy(providers = listOf(ModelDetailProvider("p", "Provider", null,
            List(12) { offering.copy(id = "service-$it") })))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model), {}) } }
        // 首末页不能越界；正文竖滑仍只滚动当前页。
        swipeContent(toLeft = false)
        compose.onNodeWithTag("model-detail-content").performTouchInput { swipeUp() }
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        scrollTo("参数量")
        val overviewTop = compose.onNodeWithText("参数量").fetchSemanticsNode().boundsInRoot.top
        swipeContent()
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
        val service = "service-8"
        scrollTo(service)
        val serviceTop = compose.onNodeWithText(service).fetchSemanticsNode().boundsInRoot.top
        // 标签栏滚出视口后仍可直接横滑，并恢复每页原来的内容位置。
        swipeContent(toLeft = false)
        compose.onNodeWithText("参数量").assertIsDisplayed()
        assertEquals(overviewTop, compose.onNodeWithText("参数量").fetchSemanticsNode().boundsInRoot.top, 1f)
        swipeContent()
        compose.onNodeWithText(service).assertIsDisplayed()
        assertEquals(serviceTop, compose.onNodeWithText(service).fetchSemanticsNode().boundsInRoot.top, 1f)
        swipeContent()
        scrollTo("暂无评测数据。")
        swipeContent()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("暂无评测数据。").assertIsDisplayed()
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:BENCHMARKS").assertIsSelected()
        // 点击可跨页跳转，随后横滑仍按当前页面继续。
        selectTab("OVERVIEW")
        swipeContent()
        compose.onNodeWithText(service).assertIsDisplayed()
        assertEquals(serviceTop, compose.onNodeWithText(service).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun onlySectionsMoveHorizontallyWhileSharedHeaderAndTabsStayInPlace() {
        show(detail())
        scrollToTabs()
        val sharedTags = listOf("model-detail-summary", "model-detail-tabs", "model-detail-header")
        val before = sharedTags.associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        // 在身份区横滑不会切换正文。
        compose.onNodeWithTag("model-detail-summary").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        // 手指尚未抬起时检查公共区域，避免只验证切页结束后的静止画面。
        compose.onNodeWithTag("model-detail-content").performTouchInput {
            down(Offset(width * 0.85f, height * 0.5f))
            moveTo(Offset(width * 0.5f, height * 0.5f), delayMillis = 300)
        }
        sharedTags.forEach { assertEquals(before.getValue(it), compose.onNodeWithTag(it).getUnclippedBoundsInRoot()) }
        compose.onAllNodes(hasTestTag("model-detail-summary"), useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("model-detail-content").performTouchInput {
            moveBy(Offset(-width * 0.35f, 0f), delayMillis = 100)
            up()
        }
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
        sharedTags.forEach { assertEquals(before.getValue(it), compose.onNodeWithTag(it).getUnclippedBoundsInRoot()) }
        compose.onNodeWithTag("detail-tab:BENCHMARKS").performClick()
        compose.onNodeWithTag("detail-tab:BENCHMARKS").assertIsSelected()
        sharedTags.forEach { assertEquals(before.getValue(it), compose.onNodeWithTag(it).getUnclippedBoundsInRoot()) }
    }

    @Test fun verticalSectionGesturesCollapseAndRestoreSharedHeader() {
        show(detail())
        scrollToTabs()
        repeat(4) { compose.onNodeWithTag("model-detail-content").performTouchInput { swipeUp() } }
        compose.onNodeWithTag("model-detail-tabs").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("Model A").assertIsDisplayed()
        // 正文回到顶部之后，同一方向手势继续向外层交接并恢复头部。
        repeat(8) { compose.onNodeWithTag("model-detail-content").performTouchInput { swipeDown() } }
        compose.onNodeWithText("Model A").assertIsDisplayed()
        compose.onNodeWithContentDescription("模型详情").assertIsDisplayed()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
    }

    @Test fun rtlSwipesFollowTabOrderInBothDirections() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(detail()), {}) }
            }
        }
        swipeContent(toLeft = false)
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
        swipeContent(toLeft = false)
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:BENCHMARKS").assertIsSelected()
        swipeContent()
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
    }

    @Test fun darkNarrowLargeFontRtlCanReachLastSection() {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(
                        name = "A very long model name that must wrap without truncation",
                    )), {}) }
                }
            }
        }
        val name = "A very long model name that must wrap without truncation"
        val headerBounds = compose.onNodeWithTag("model-detail-header").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("model-detail-scroll").performScrollToNode(hasText(name))
        assertTextNotClipped(name)
        scrollTo("131,072")
        assertTextNotClipped("131,072")
        listOf("Complete model license longer than sixteen characters").forEach { text ->
            scrollTo(text)
            assertTextNotClipped(text)
        }
        scrollTo("模型许可")
        compose.onNodeWithText("模型许可").assertIsDisplayed()
        compose.onNodeWithContentDescription(name).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
        assertEquals(headerBounds, compose.onNodeWithTag("model-detail-header").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun darkNarrowLargeFontBenchmarkLabelsAndLongMetadataDoNotClip() {
        val name = "Long evaluation name with complete version and configuration"
        val harness = "Long evaluation framework identifier without truncation"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { ModelDetailScreen(content(detail().copy(
                        benchmarks = listOf(ModelBenchmark(name, 95.12345, harness = harness)),
                    )), {}) }
                }
            }
        }
        scrollToTabs()
        listOf("OVERVIEW" to "概览", "PROVIDERS" to "价格", "BENCHMARKS" to "评测").forEach { (tab, label) ->
            compose.onNodeWithTag("detail-tab:$tab").performScrollTo()
            assertTextNotClipped(label)
        }
        selectTab("BENCHMARKS")
        listOf(name, "95.12345", harness).forEach {
            scrollTo(it)
            compose.onNodeWithText(it).assertIsDisplayed()
            assertTextNotClipped(it)
        }
    }

    private fun textLayout(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        // 图文分区合并读屏语义，但每个文字仍须真正滚入视口后检查实际排版。
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals("$text must expose its actual text layout", 1, layouts.size)
        return layouts.single()
    }

    private fun assertTextNotClipped(text: String) {
        val layout = textLayout(text)
        // RTL Text 的 paragraph 约束宽度可能大于实际布局，不只依赖 hasVisualOverflow。
        assertFalse("$text height overflow", layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            assertTrue("$text clips at start", layout.getLineLeft(line) >= -1f)
            assertTrue("$text clips at end", layout.getLineRight(line) <= layout.size.width + 1f)
            assertFalse("$text is ellipsized", layout.isLineEllipsized(line))
        }
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun imageDiffers(first: androidx.compose.ui.graphics.ImageBitmap, second: androidx.compose.ui.graphics.ImageBitmap): Boolean {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        return (0 until a.height).any { y -> (0 until a.width).any { x -> a[x, y] != b[x, y] } }
    }

    private fun assertHeaderAndInsets(darkTheme: Boolean) {
        var background = Color.Unspecified
        compose.setContent {
            ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                background = MaterialTheme.colorScheme.surfaceContainerLowest
                ModelDetailScreen(content(detail().copy(
                    id = "deepseek/example", license = "MIT",
                    metadata = detail().metadata.copy(releaseDate = "2026-10-17", outputLimitTokens = 384_000),
                )), {}, windowInsets = WindowInsets(0, 72, 0, 48))
            }
        }
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(0)
        assertTrue(compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot.top >= 72f)
        val screen = compose.onNodeWithTag("model-detail-screen")
        val screenBounds = screen.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("model-detail-metrics").assertDoesNotExist()
        val hero = compose.onNodeWithTag("model-detail-hero").fetchSemanticsNode().boundsInRoot
        val pixels = screen.captureToImage().toPixelMap()
        val above = (hero.top + hero.height * 0.25f - screenBounds.top).toInt()
        val below = (hero.top + hero.height * 0.75f - screenBounds.top).toInt()
        assertEquals("Header must use the page background", background.toArgb(), pixels[1, above].toArgb())
        assertEquals("Content must use the same page background", background.toArgb(), pixels[1, below].toArgb())
        val lastField = compose.onNodeWithText("模型许可")
        scrollTo("模型许可")
        // 图文许可行整体增高；滚到正文末端后验证完整末项能离开系统导航区。
        compose.onNodeWithTag("model-detail-content").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, screenBounds.height)
        }
        val bottom = with(compose.density) { lastField.getUnclippedBoundsInRoot().bottom.toPx() }
        assertTrue("Last text must avoid the navigation bar", bottom <= screenBounds.bottom - 48f)
    }

    private fun show(model: ModelDetail) {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model), {}) } }
    }
    private fun scrollTo(text: String) {
        scrollToSections()
        compose.onNodeWithTag("model-detail-content").performScrollToNode(hasText(text))
    }
    private fun swipeContent(toLeft: Boolean = true) {
        scrollToSections()
        compose.onNodeWithTag("model-detail-content").performTouchInput {
            if (toLeft) swipeLeft(startX = width * 0.85f, endX = width * 0.15f)
            else swipeRight(startX = width * 0.15f, endX = width * 0.85f)
        }
    }
    private fun selectTab(name: String) {
        scrollToTabs()
        val tab = compose.onNodeWithTag("detail-tab:$name")
        if (!tab.isDisplayed()) tab.performScrollTo()
        tab.performClick()
    }
    private fun scrollToTabs() {
        compose.onNodeWithTag("model-detail-scroll").performScrollToNode(hasTestTag("model-detail-tabs"))
    }
    private fun scrollToSections() {
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(2)
    }
    private fun content(model: ModelDetail) = ModelDetailUiState(model.id, ModelDetailStatus.CONTENT, model)
    private fun detail() = ModelDetail(
        "lab/a", "Model A", fetchedAtEpochMillis = 100,
        license = "Complete model license longer than sixteen characters",
        weights = listOf(ModelWeight("https://example.org/weights", "模型权重", "FP8", "safetensors")),
        metadata = ModelDetailMetadata(contextLimitTokens = 131_072, attachment = true, reasoning = false,
            inputModalities = listOf("text", "future"), outputModalities = emptyList()),
    )
}
