package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.foundation.layout.Box
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

    @Test fun overviewDisplaysCapabilitiesExactParametersAndFullLicense() {
        show(detail())
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        scrollTo("支持")
        compose.onNodeWithText("支持").assertIsDisplayed()
        scrollTo("不支持")
        compose.onNodeWithText("不支持").assertIsDisplayed()
        scrollTo("工具调用")
        compose.onNode(hasText("工具调用") and hasText("暂无数据")).assertIsDisplayed()
        scrollTo("技术参数")
        compose.onNodeWithText("技术参数").assertIsDisplayed()
        scrollTo("131,072 tokens")
        compose.onNodeWithText("131,072 tokens").assertIsDisplayed()
        scrollTo("Complete model license longer than sixteen characters")
        compose.onNodeWithText("Complete model license longer than sixteen characters").assertIsDisplayed()
        selectTab("PROVIDERS")
        scrollTo("暂无显式关联的 API 服务。")
        compose.onNodeWithText("暂无显式关联的 API 服务。").assertIsDisplayed()
    }

    @Test fun centeredHeroAndIndependentCompactMetricsPrecedeAllThreeTabs() {
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
            scrollToMetric("上下文长度：131,072 tokens")
            compose.onNodeWithContentDescription("上下文长度：131,072 tokens").assertIsDisplayed()
            compose.onNode(hasText("131.1K") and sharedHeader, useUnmergedTree = true).assertIsDisplayed()
            compose.onNode(hasText("384K") and sharedHeader, useUnmergedTree = true).assertIsDisplayed()
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
        compose.onNodeWithText("开放权重：暂无数据").assertIsDisplayed()
        compose.onNode(hasText("暂无数据") and hasAnyAncestor(hasTestTag("input-modalities")), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNode(hasText("无") and hasAnyAncestor(hasTestTag("output-modalities")), useUnmergedTree = true)
            .assertIsDisplayed()
        scrollToMetric("上下文长度：0 tokens")
        compose.onNodeWithContentDescription("上下文长度：0 tokens").assertIsDisplayed()
        compose.onNodeWithContentDescription("发布时间：2026").assertIsDisplayed()
    }

    @Test fun lightHeaderUsesPageBackgroundAndKeepsThreeColumnMetricsAndInsets() {
        assertHeaderMetricsAndInsets(darkTheme = false)
    }

    @Test fun darkHeaderUsesPageBackgroundAndKeepsThreeColumnMetricsAndInsets() {
        assertHeaderMetricsAndInsets(darkTheme = true)
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
        scrollTo("131,072 tokens")
        val before = compose.onNodeWithText("131,072 tokens").fetchSemanticsNode().boundsInRoot.top
        // 恢复概览内的精确参数阅读位置时，也应保留当时的标题状态。
        val compactTitleBefore = compose.onNodeWithContentDescription(model.name).isDisplayed()
        compose.runOnIdle { state.value = ModelDetailUiState(model.id) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = content(model) }
        compose.onNodeWithText("131,072 tokens").assertIsDisplayed()
        assertEquals(compactTitleBefore, compose.onNodeWithContentDescription(model.name).isDisplayed())
        assertEquals(before, compose.onNodeWithText("131,072 tokens").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        val tabsBefore = compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("detail-tab:BENCHMARKS").performClick()
        scrollTo("暂无评测数据。")
        compose.onNodeWithText("暂无评测数据。").assertIsDisplayed()
        selectTab("OVERVIEW")
        assertEquals(tabsBefore, compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollTo("131,072 tokens")
        compose.onNodeWithText("131,072 tokens").assertIsDisplayed()
    }

    @Test fun showsEveryServiceAndRestoresExpandedPreciseTierAndServiceParameters() {
        val restoration = StateRestorationTester(compose)
        val metadata = ModelDetailMetadata(contextLimitTokens = 1024)
        val offering = ModelDetailOffering(
            "service-a", "Service A", metadata, "beta",
            ModelDetailPrice(input = 0.0, output = 0.003, tiers = listOf(
                ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 1.0, reasoning = 0.004)),
            )), null, null, null, "models.dev", 100,
        )
        val model = detail().copy(providers = listOf(ModelDetailProvider("p", "Provider", null,
            listOf(offering, offering.copy(id = "service-b", name = "Service B", price = null)))))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model), {}) } }
        selectTab("PROVIDERS")
        scrollTo("Service A")
        scrollTo("$0.003")
        compose.onNodeWithText("$0.00").assertIsDisplayed()
        compose.onNodeWithText("$0.003").assertIsDisplayed()
        scrollTo("展开档位价格")
        compose.onNodeWithText("展开档位价格").performClick()
        scrollTo("上下文长度档位门槛：272,000 tokens")
        compose.onNodeWithText("上下文长度档位门槛：272,000 tokens").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("上下文长度档位门槛：272,000 tokens").assertIsDisplayed()
        scrollTo("展开服务参数与来源")
        compose.onNodeWithTag("service-details:p:service-a").performClick()
        scrollTo("1,024 tokens")
        compose.onNodeWithText("1,024 tokens").assertIsDisplayed()
        scrollTo("Service B")
        compose.onNodeWithText("Service B").assertIsDisplayed()
        scrollTo("价格：暂无数据")
        compose.onNodeWithText("价格：暂无数据").assertIsDisplayed()
    }

    @Test fun invalidAndUnavailableExternalLinksNeverCrash() {
        var calls = 0
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { calls++; throw IllegalArgumentException("no browser") }
            }) {
                ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(detail().copy(links = listOf(
                    ModelLink("javascript:alert(1)", "Unsafe", null),
                    ModelLink("https://example.org", "Safe", null),
                ))), {}) }
            }
        }
        scrollTo("打开：Unsafe（外部链接）")
        compose.onNodeWithText("打开：Unsafe（外部链接）").assertIsNotEnabled()
        scrollTo("打开：Safe（外部链接）")
        compose.onNodeWithText("打开：Safe（外部链接）").performClick()
        scrollTo("无法打开链接，请确认设备上有可用浏览器。")
        compose.onNodeWithText("无法打开链接，请确认设备上有可用浏览器。").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun keepsMultipleProvidersAndIndependentModesSeparate() {
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
        scrollTo("独立模式：fast")
        compose.onNodeWithText("独立模式：fast").assertIsDisplayed()
        scrollTo("$0.75")
        compose.onNodeWithText("$0.75").assertIsDisplayed()
        scrollTo("Second provider")
        compose.onNodeWithText("Second provider").assertIsDisplayed()
        scrollTo("Second service")
        compose.onNodeWithText("Second service").assertIsDisplayed()
    }

    @Test fun restoringThroughAsyncLoadingDoesNotClampTheSavedContentPosition() {
        val restoration = StateRestorationTester(compose)
        val model = detail()
        val state = mutableStateOf(content(model))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        scrollTo("本地获取时间")
        val before = compose.onNodeWithText("本地获取时间").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = ModelDetailUiState(model.id) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("正在读取模型详情…").assertIsDisplayed()
        compose.runOnIdle { state.value = content(model) }
        compose.onNodeWithText("本地获取时间").assertIsDisplayed()
        compose.onNodeWithContentDescription(model.name).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("本地获取时间").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun headerBecomesModelNameAndStaysReachableWhileTabsScrollAway() {
        var backs = 0
        val model = detail().copy(links = List(12) { ModelLink("https://example.org/$it", "Link $it", null) })
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelDetailScreen(content(model), { backs++ })
        } }
        val header = compose.onNodeWithTag("model-detail-header")
        val initialBounds = header.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("模型详情").assertIsDisplayed()
        compose.onNodeWithContentDescription(model.name).assertDoesNotExist()
        scrollToTabs()
        compose.onNodeWithTag("model-detail-tabs").assertIsDisplayed()
        scrollTo("本地获取时间")
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
        show(detail().copy(links = List(12) { ModelLink("https://example.org/$it", "Link $it", null) }))
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

    @Test fun switchingTabsPreservesScrollExpansionAndSelectionThroughAsyncRestoration() {
        val restoration = StateRestorationTester(compose)
        val offering = ModelDetailOffering("s", "Service", ModelDetailMetadata(), null,
            ModelDetailPrice(input = 0.0, tiers = listOf(
                ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 0.003)),
            )), null, null, null, "models.dev", 100)
        val model = detail().copy(providers = listOf(ModelDetailProvider("p", "Provider", null, listOf(offering))))
        val state = mutableStateOf(content(model))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        selectTab("PROVIDERS")
        scrollTo("展开档位价格")
        compose.onNodeWithText("展开档位价格").performClick()
        scrollTo("展开服务参数与来源")
        compose.onNodeWithTag("service-details:p:s").performClick()
        // 标签也随列表滚动，先滚回标签所在条目再切换。
        scrollToTabs()
        val before = compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("detail-tab:OVERVIEW").performClick()
        selectTab("BENCHMARKS")
        selectTab("PROVIDERS")
        assertEquals(before, compose.onNodeWithTag("model-detail-tabs").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollTo("上下文长度档位门槛：272,000 tokens")
        compose.onNodeWithText("上下文长度档位门槛：272,000 tokens").assertIsDisplayed()
        scrollTo("收起服务参数与来源")
        compose.onNodeWithText("收起服务参数与来源").assertIsDisplayed()
        val contentBefore = compose.onNodeWithText("收起服务参数与来源").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { state.value = ModelDetailUiState(model.id) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = content(model) }
        compose.onNodeWithText("收起服务参数与来源").assertIsDisplayed()
        assertEquals(contentBefore, compose.onNodeWithText("收起服务参数与来源").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
    }

    @Test fun swipingSectionsPreservesDeepScrollExpansionAndRestoresSelectedPage() {
        val restoration = StateRestorationTester(compose)
        val offering = ModelDetailOffering("s", "Service", ModelDetailMetadata(), null,
            ModelDetailPrice(input = 0.0, tiers = listOf(
                ModelDetailPriceTier("context", 272_000, ModelDetailPrice(input = 0.003)),
            )), null, null, null, "models.dev", 100)
        val model = detail().copy(providers = listOf(ModelDetailProvider("p", "Provider", null, listOf(offering))))
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model), {}) } }
        // 首末页不能越界；正文竖滑仍只滚动当前页。
        swipeContent(toLeft = false)
        compose.onNodeWithTag("model-detail-content").performTouchInput { swipeUp() }
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:OVERVIEW").assertIsSelected()
        scrollTo("本地获取时间")
        val overviewTop = compose.onNodeWithText("本地获取时间").fetchSemanticsNode().boundsInRoot.top
        swipeContent()
        scrollToTabs()
        compose.onNodeWithTag("detail-tab:PROVIDERS").assertIsSelected()
        scrollTo("展开档位价格")
        compose.onNodeWithText("展开档位价格").performClick()
        val tier = "上下文长度档位门槛：272,000 tokens"
        scrollTo(tier)
        val tierTop = compose.onNodeWithText(tier).fetchSemanticsNode().boundsInRoot.top
        // 标签栏滚出视口后仍可直接横滑，并恢复每页原来的内容位置。
        swipeContent(toLeft = false)
        compose.onNodeWithText("本地获取时间").assertIsDisplayed()
        assertEquals(overviewTop, compose.onNodeWithText("本地获取时间").fetchSemanticsNode().boundsInRoot.top, 1f)
        swipeContent()
        compose.onNodeWithText(tier).assertIsDisplayed()
        assertEquals(tierTop, compose.onNodeWithText(tier).fetchSemanticsNode().boundsInRoot.top, 1f)
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
        compose.onNodeWithText(tier).assertIsDisplayed()
        assertEquals(tierTop, compose.onNodeWithText(tier).fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun onlySectionsMoveHorizontallyWhileSharedHeaderAndTabsStayInPlace() {
        show(detail())
        scrollToTabs()
        val sharedTags = listOf("model-detail-summary", "model-detail-metrics", "model-detail-tabs", "model-detail-header")
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
        scrollTo("131,072 tokens")
        assertTextNotClipped("131,072 tokens")
        listOf("Complete model license longer than sixteen characters").forEach { text ->
            scrollTo(text)
            assertTextNotClipped(text)
        }
        scrollTo("本地获取时间")
        compose.onNodeWithText("本地获取时间").assertIsDisplayed()
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

    private fun assertTextNotClipped(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
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

    private fun assertHeaderMetricsAndInsets(darkTheme: Boolean) {
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
        val card = compose.onNodeWithTag("model-detail-metrics").fetchSemanticsNode().boundsInRoot
        val pixels = screen.captureToImage().toPixelMap()
        val above = (card.top + card.height * 0.25f - screenBounds.top).toInt()
        val below = (card.top + card.height * 0.75f - screenBounds.top).toInt()
        assertEquals("Header must use the page background", background.toArgb(), pixels[1, above].toArgb())
        assertEquals("Content must use the same page background", background.toArgb(), pixels[1, below].toArgb())
        val date = compose.onNodeWithTag("release-date").fetchSemanticsNode().boundsInRoot
        val context = compose.onNodeWithTag("context-limit").fetchSemanticsNode().boundsInRoot
        val output = compose.onNodeWithTag("output-limit").fetchSemanticsNode().boundsInRoot
        assertTrue(date.right <= context.left)
        assertTrue(context.right <= output.left)
        assertEquals(date.center.y, context.center.y, 1f)
        assertEquals(context.center.y, output.center.y, 1f)
        val notice = compose.onNode(hasText("完整来源和 MIT 许可见设置", substring = true))
        scrollToSections()
        compose.onNodeWithTag("model-detail-content").performScrollToNode(hasText("完整来源和 MIT 许可见设置", substring = true))
        val bottom = with(compose.density) { notice.getUnclippedBoundsInRoot().bottom.toPx() }
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
    private fun scrollToMetric(description: String) {
        compose.onNodeWithTag("model-detail-scroll")
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription(description))
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
        metadata = ModelDetailMetadata(contextLimitTokens = 131_072, attachment = true, reasoning = false,
            inputModalities = listOf("text", "future"), outputModalities = emptyList()),
    )
}
