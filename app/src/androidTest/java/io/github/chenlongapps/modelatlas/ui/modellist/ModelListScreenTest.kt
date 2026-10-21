package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun updatePromptKeepsCachedListAndHasExplicitApplyAction() {
        var applies = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(
                    uiState = ModelListUiState(
                        models = listOf(ModelSummary("lab/cached", "本地模型")),
                        isLoading = false,
                        updateAvailable = true,
                    ),
                    onApplyUpdate = { applies++ },
                )
            }
        }
        compose.onNodeWithText("本地模型").assertIsDisplayed()
        compose.onNodeWithText("发现新数据，可更新模型目录").assertIsDisplayed()
        val header = compose.onNodeWithTag("catalog-header").getUnclippedBoundsInRoot()
        val notice = compose.onNodeWithTag("catalog-notice").getUnclippedBoundsInRoot()
        val order = compose.onNodeWithText("按发布时间 · 最新在前").getUnclippedBoundsInRoot()
        assertTrue("Notice must follow the header", notice.top >= header.bottom)
        assertTrue("Notice must precede the sort order", notice.bottom <= order.top)
        assertEquals(0, applies)
        compose.onNodeWithText("立即更新").performClick()
        assertEquals(1, applies)
    }

    @Test fun pullAtTopRequestsRefreshWithoutRemovingCachedModels() {
        assertPullRequestsRefresh(listOf(ModelSummary("lab/cached", "本地模型")))
        compose.onNodeWithText("本地模型").assertIsDisplayed()
    }

    @Test fun emptyCatalogCanAlsoBePulledToRefresh() {
        assertPullRequestsRefresh(emptyList())
    }

    @Test fun refreshFailureIsNonBlockingAndKeepsCatalogCount() {
        show(ModelListUiState(
            models = listOf(ModelSummary("lab/cached", "本地模型")),
            isLoading = false,
            refreshError = ModelListError.NETWORK,
        ))
        compose.onNodeWithText("本地模型").assertIsDisplayed()
        compose.onNodeWithText("模型目录 · 1 个模型").assertIsDisplayed()
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        compose.onNodeWithText("暂时无法加载").assertDoesNotExist()
    }

    @Test fun catalogNoticeScrollsAwayAndReturnsWithTheHeader() {
        show(ModelListUiState(
            models = List(40) { ModelSummary("lab/model-$it", "模型 $it") },
            isLoading = false,
            updateAvailable = true,
        ))
        compose.onNodeWithTag("catalog-notice").assertIsDisplayed()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/model-20"))
        compose.onNodeWithTag("catalog-notice").assertIsNotDisplayed()
        compose.onNodeWithText("立即更新").assertIsNotDisplayed()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-header"))
        compose.onNodeWithTag("catalog-notice").assertIsDisplayed()
        compose.onNodeWithText("立即更新").assertIsDisplayed()
    }

    @Test fun incomingUpdateNoticeDoesNotScrollTheUserBackToTheHeader() {
        val state = mutableStateOf(ModelListUiState(
            models = List(40) { ModelSummary("lab/model-$it", "模型 $it") },
            isLoading = false,
        ))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) { ModelListScreen(state.value) }
        }
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/model-20"))
        val model = compose.onNodeWithTag("model:lab/model-20")
        val before = model.getUnclippedBoundsInRoot().top
        compose.runOnIdle { state.value = state.value.copy(updateAvailable = true) }
        model.assertIsDisplayed()
        assertEquals(before.value, model.getUnclippedBoundsInRoot().top.value, 1f)
        compose.onNodeWithTag("catalog-notice").assertIsNotDisplayed()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-header"))
        compose.onNodeWithText("发现新数据，可更新模型目录").assertIsDisplayed()
    }

    private fun assertPullRequestsRefresh(models: List<ModelSummary>) {
        var refreshes = 0
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(
                    uiState = ModelListUiState(models = models, isLoading = false),
                    onRefresh = { refreshes++ },
                )
            }
        }
        compose.onNodeWithTag("model-list").performTouchInput {
            swipeDown(startY = height * 0.2f, endY = height * 0.9f, durationMillis = 600)
        }
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun lightPriceSectionSharesTheModelCardBackground() {
        assertFlatPriceSection(darkTheme = false)
    }

    @Test fun darkPriceSectionSharesTheModelCardBackground() {
        assertFlatPriceSection(darkTheme = true)
    }

    @Test fun lightStatusBarShowsScrolledContentWithoutLosingSafeAreas() {
        assertStatusBarShowsScrolledContent(darkTheme = false)
    }

    @Test fun darkStatusBarShowsScrolledContentWithoutLosingSafeAreas() {
        assertStatusBarShowsScrolledContent(darkTheme = true)
    }

    @Test fun largeFontAndTallCutoutKeepHeaderSafeAndStatusBarTranslucent() {
        assertStatusBarShowsScrolledContent(darkTheme = false, fontScale = 2f)
    }

    @Test fun headerMovesAtTheSameSpeedAsTheListWhileDragging() {
        show(ModelListUiState(
            models = List(40) { ModelSummary("lab/model-$it", "模型 $it") },
            isLoading = false,
        ))
        val title = compose.onNodeWithText("Model Atlas")
        val initialTitleTop = title.fetchSemanticsNode().boundsInRoot.top
        val header = compose.onNodeWithTag("catalog-header")
        val firstModel = compose.onNodeWithTag("model:lab/model-0")
        val initialHeaderTop = header.getUnclippedBoundsInRoot().top
        val initialModelTop = firstModel.getUnclippedBoundsInRoot().top
        val dragDistance = with(compose.density) { 32.dp.toPx() }

        // 保持手指按下，检查跟手位移，而非仅检查松手后的隐藏结果。
        compose.onNodeWithTag("model-list").performTouchInput {
            down(center)
            moveBy(Offset(0f, -dragDistance / 2), delayMillis = 100)
            moveBy(Offset(0f, -dragDistance / 2), delayMillis = 100)
        }
        try {
            assertTrue("Title must move with the list drag", title.fetchSemanticsNode().boundsInRoot.top < initialTitleTop)
            val headerMovement = initialHeaderTop - header.getUnclippedBoundsInRoot().top
            val modelMovement = initialModelTop - firstModel.getUnclippedBoundsInRoot().top
            assertEquals("Header must scroll as ordinary list content", modelMovement.value, headerMovement.value, 1f)
        } finally {
            compose.onNodeWithTag("model-list").performTouchInput { up() }
        }
    }

    @Test fun headerOnlyReappearsWhenScrollingBackToTheStart() {
        show(ModelListUiState(
            models = List(40) { ModelSummary("lab/model-$it", "模型 $it") },
            isLoading = false,
        ))
        compose.onNodeWithText("Model Atlas").assertIsDisplayed()
        compose.onNodeWithTag("model-list")
            .performScrollToNode(hasTestTag("model:lab/model-20"))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText("Model Atlas").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("数据来源与许可").assertDoesNotExist()

        compose.onNodeWithTag("model-list").performTouchInput { swipeDown() }
        compose.onNodeWithText("Model Atlas").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("数据来源与许可").assertDoesNotExist()
        compose.onNodeWithTag("model:lab/model-0").assertDoesNotExist()

        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-header"))
        compose.onNodeWithText("Model Atlas").assertIsDisplayed()
        compose.onNodeWithText("模型目录 · 40 个模型").assertIsDisplayed()
    }

    @Test fun showsNameLabCompactMetadataAndDirectionalModalities() {
        show(ModelListUiState(
            models = listOf(ModelSummary(
                id = "lab/model/version",
                name = "规范模型",
                releaseDate = "2026-10",
                contextLimitTokens = 5_000_000_000L,
                outputLimitTokens = 16_384L,
                inputModalities = listOf("text", "image", "video", "pdf"),
                outputModalities = listOf("text", "audio", "future-modality"),
            )),
            isLoading = false,
        ))
        compose.onNodeWithText("规范模型").assertIsDisplayed()
        compose.onNodeWithTag("model-lab").assertIsDisplayed()
        assertField("model:lab/model/version", "lab")
        assertField("release-date", "2026-10")
        assertField("context-limit", "5000M")
        compose.onNodeWithContentDescription("上下文长度：5,000,000,000 tokens").assertIsDisplayed()
        assertField("output-limit", "16.4K")
        compose.onNodeWithContentDescription("最大输出：16,384 tokens").assertIsDisplayed()
        listOf("文本", "图片", "视频", "PDF").forEach { assertModalityIcon("input-modalities", it) }
        listOf("文本", "音频", "future-modality").forEach { assertModalityIcon("output-modalities", it) }
        compose.onNodeWithTag("modalities-direction", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("输出：", substring = true).assertDoesNotExist()
        listOf("输入", "输出").forEach {
            compose.onNodeWithContentDescription(it, useUnmergedTree = true).assertIsDisplayed()
        }
        compose.onNodeWithText("模型目录 · 1 个模型").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("API 服务商：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("本地示例数据", substring = true).assertDoesNotExist()
    }

    @Test fun labLogoUsesBundledAssetForKnownLab() {
        show(ModelListUiState(listOf(
            ModelSummary(id = "openai/gpt-5", name = "有素材模型"),
            ModelSummary(id = "meta/llama-5", name = "另一个有素材模型"),
        ), isLoading = false))
        listOf("openai/gpt-5", "meta/llama-5").forEach { id ->
            compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:$id"))
            compose.onNode(hasTestTag(LabLogoTag) and hasAnyAncestor(hasTestTag("model:$id")), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag(LabLogoFallbackTag).assertDoesNotExist()
    }

    @Test fun labLogoFallsBackWhenNoBundledAsset() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "acme/atlas-mini",
            name = "无素材模型",
        )), isLoading = false))
        compose.onNodeWithTag(LabLogoFallbackTag).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LabLogoTag).assertDoesNotExist()
        assertField("model:acme/atlas-mini", "acme")
    }

    @Test fun labLogoFallsBackWhenLabIsMissing() {
        show(ModelListUiState(listOf(ModelSummary("unlinked", "未关联模型")), isLoading = false))
        compose.onNodeWithTag(LabLogoFallbackTag).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LabLogoTag).assertDoesNotExist()
        compose.onNode(hasTestTag("model-lab") and hasText("暂无数据")).assertIsDisplayed()
    }

    @Test fun missingMetadataIsNotInvented() {
        show(ModelListUiState(listOf(ModelSummary("unlinked", "未关联模型")), isLoading = false))
        listOf("release-date", "context-limit", "output-limit").forEach {
            assertField(it, "暂无数据")
        }
        assertModalityIcon("input-modalities", "暂无数据")
        assertModalityIcon("output-modalities", "暂无数据")
    }

    @Test fun emptyModalitiesAndZeroLimitAreNotTreatedAsMissing() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/model",
            name = "模型",
            outputLimitTokens = 0L,
            inputModalities = emptyList(),
            outputModalities = listOf("text"),
        )), isLoading = false))
        assertField("output-limit", "0")
        compose.onNodeWithContentDescription("最大输出：0 tokens").assertIsDisplayed()
        assertModalityIcon("input-modalities", "无")
        assertModalityIcon("output-modalities", "文本")
    }

    @Test fun openWeightsAndLicenseAreReportedWithoutImplyingOpenSource() {
        show(ModelListUiState(listOf(
            ModelSummary(
                id = "lab/open",
                name = "开放权重模型",
                openWeights = true,
                license = "Apache-2.0",
            ),
            ModelSummary(
                id = "lab/open-without-license",
                name = "无许可信息的公开权重模型",
                openWeights = true,
            ),
            ModelSummary(
                id = "lab/closed",
                name = "未公开权重模型",
                openWeights = false,
            ),
        ), isLoading = false))
        listOf(
            "开放权重 · Apache-2.0",
            "开放权重",
            "权重未公开",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
    }

    @Test fun listKeepsShortLicenseButOmitsLongLicenseName() {
        val longLicense = "Very Long Model Community License Agreement"
        show(ModelListUiState(listOf(
            ModelSummary(id = "lab/mit", name = "MIT 模型", openWeights = true, license = "MIT"),
            ModelSummary(id = "lab/long-license", name = "长许可模型", openWeights = true, license = longLicense),
        ), isLoading = false))
        compose.onNodeWithText("开放权重 · MIT").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("开放权重").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(longLicense, substring = true).assertDoesNotExist()
    }

    @Test fun listAbbreviatesMillionContextAndThousandOutput() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/compact", name = "紧凑模型",
            contextLimitTokens = 1_000_000, outputLimitTokens = 384_000,
        )), isLoading = false))
        assertField("context-limit", "1M")
        assertField("output-limit", "384K")
        compose.onNodeWithContentDescription("上下文长度：1,000,000 tokens").assertIsDisplayed()
        compose.onNodeWithContentDescription("最大输出：384,000 tokens").assertIsDisplayed()
    }

    @Test fun missingOpenWeightsAndLicenseAreNotInvented() {
        show(ModelListUiState(listOf(ModelSummary("unlinked", "未关联模型", license = "MIT")), isLoading = false))
        compose.onNodeWithText("开放权重：暂无数据").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("MIT", substring = true).assertDoesNotExist()
    }

    @Test fun priceShowsInputOutputAndCacheHitWithoutNamingProvider() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "deepseek/deepseek-v4.1-flash",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "deepseek",
                providerName = "DeepSeek",
                isOfficial = true,
                input = 0.60,
                output = 0.15,
                cacheRead = 0.003,
            ),
        )), isLoading = false))
        listOf(
            "API 价格",
            "美元 / 1M tokens",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
        assertField("price-input", "$0.60")
        assertField("price-output", "$0.15")
        assertField("price-cache-read", "缓存命中输入")
        assertField("price-cache-read", "$0.003")
        compose.onNodeWithText("非高峰", substring = true).assertDoesNotExist()
        compose.onNodeWithText("DeepSeek").assertDoesNotExist()
        compose.onNodeWithText("官方").assertDoesNotExist()
        compose.onNodeWithText("第三方").assertDoesNotExist()
    }

    @Test fun priceMarksNothingAsOfficialOrThirdParty() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "microsoft/phi-x",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "openrouter",
                providerName = "OpenRouter",
                isOfficial = false,
                input = 0.5,
                output = 1.5,
            ),
        )), isLoading = false))
        assertField("price-input", "$0.50")
        assertField("price-output", "$1.50")
        compose.onNodeWithText("OpenRouter").assertDoesNotExist()
    }

    @Test fun missingPriceIsNotInventedAsZero() {
        show(ModelListUiState(listOf(ModelSummary("lab/model", "模型")), isLoading = false))
        compose.onNodeWithText("价格：暂无数据").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("price-input").assertDoesNotExist()
        compose.onNodeWithTag("price-output").assertDoesNotExist()
    }

    @Test fun zeroPriceIsReportedAsZero() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/model",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "p",
                providerName = "P",
                isOfficial = false,
                input = 0.0,
                output = 0.0,
            ),
        )), isLoading = false))
        assertField("price-input", "$0.00")
        assertField("price-output", "$0.00")
    }

    @Test fun partialPriceKeepsMissingCategoriesAsNoData() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/model",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "p",
                providerName = "P",
                isOfficial = false,
                output = 4.0,
            ),
        )), isLoading = false))
        assertModalityIcon("input-modalities", "暂无数据")
        assertField("price-input", "暂无数据")
        assertField("price-output", "$4.00")
    }

    @Test fun narrowDarkCardWithLargeFontKeepsAllPriceColumnsReadable() {
        var headerStyle = TextStyle.Default
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    headerStyle = MaterialTheme.typography.labelSmall
                    ModelListScreen(
                        uiState = ModelListUiState(listOf(ModelSummary(
                            id = "deepseek/model",
                            name = "长名称 DeepSeek Model",
                            price = ModelPriceSummary(
                                providerId = "deepseek", providerName = "DeepSeek", isOfficial = true,
                                input = 1e-19, output = 123.45, cacheRead = 0.0,
                            ),
                        )), isLoading = false),
                        modifier = Modifier.width(320.dp),
                    )
                }
            }
        }
        listOf("API 价格", "美元 / 1M tokens").forEach { text ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertEquals("Price header must use compact typography", headerStyle.fontSize, layout.layoutInput.style.fontSize)
            assertEquals("Price header must use compact line height", headerStyle.lineHeight, layout.layoutInput.style.lineHeight)
            assertNoTextClipping(text, layout)
        }
        listOf(
            "price-input" to "$0.0000000000000000001", "price-output" to "$123.45", "price-cache-read" to "$0.00",
        ).forEach { (tag, value) ->
            assertField(tag, value)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertNoTextClipping(tag, layouts.single())
        }
        compose.onNodeWithContentDescription("展开档位价格").assertDoesNotExist()
    }

    @Test fun cacheWriteReasoningAndAudioAreShownWhenProvided() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "anthropic/claude-x",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "anthropic",
                providerName = "Anthropic",
                isOfficial = true,
                input = 5.0,
                output = 25.0,
                cacheRead = 0.5,
                cacheWrite = 6.25,
                reasoning = 25.0,
                inputAudio = 1.0,
                outputAudio = 2.0,
            ),
        )), isLoading = false))
        listOf(
            "缓存写入：$6.25", "推理：$25.00",
            "音频输入：$1.00", "音频输出：$2.00",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
        assertField("price-cache-read", "$0.50")
    }

    @Test fun absentCacheReasoningAndAudioLinesAreNotInvented() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/model",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "p",
                providerName = "P",
                isOfficial = false,
                input = 1.0,
                output = 2.0,
            ),
        )), isLoading = false))
        listOf("缓存命中输入", "缓存写入", "推理", "音频输入", "音频输出").forEach { label ->
            compose.onNodeWithText(label, substring = true).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("展开档位价格").assertDoesNotExist()
    }

    @Test fun tierPricesExpandOnDemand() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "anthropic/claude-x",
            name = "模型",
            price = ModelPriceSummary(
                providerId = "anthropic",
                providerName = "Anthropic",
                isOfficial = true,
                input = 5.0,
                output = 25.0,
                tiers = listOf(
                    ModelPriceTierSummary("context", 272_000, input = 10.0, output = 37.5),
                ),
            ),
        )), isLoading = false))
        compose.onNodeWithContentDescription("展开档位价格").performScrollTo().assertIsDisplayed()
        val headerBounds = compose.onNodeWithContentDescription("展开档位价格").getUnclippedBoundsInRoot()
        assertTrue("Tier header must not reserve 48dp of layout height",
            headerBounds.bottom - headerBounds.top <= 20.dp)
        compose.onNodeWithContentDescription("展开档位价格").assertTouchHeightIsEqualTo(48.dp)
        assertPriceChevronPointsDown(pointsDown = true)
        compose.onNodeWithText("上下文 ≥ 272,000 tokens：输入 $10.00 · 输出 $37.50").assertDoesNotExist()

        compose.onNodeWithContentDescription("展开档位价格").performScrollTo().performClick()
        compose.onNodeWithText("上下文 ≥ 272,000 tokens：输入 $10.00 · 输出 $37.50")
            .performScrollTo().assertIsDisplayed()
        assertPriceChevronPointsDown(pointsDown = false)

        compose.onNodeWithContentDescription("收起档位价格").performScrollTo().performClick()
        assertPriceChevronPointsDown(pointsDown = true)
        compose.onNodeWithText("上下文 ≥ 272,000 tokens：输入 $10.00 · 输出 $37.50").assertDoesNotExist()

        // 实际点击标题布局下方的扩展触摸区域，验证紧凑布局没有缩小可点击范围。
        val bounds = compose.onNodeWithContentDescription("展开档位价格").fetchSemanticsNode().boundsInRoot
        val belowHeader = with(compose.density) { 4.dp.toPx() }
        compose.onRoot().performTouchInput { click(Offset(bounds.center.x, bounds.bottom + belowHeader)) }
        compose.onNodeWithText("上下文 ≥ 272,000 tokens：输入 $10.00 · 输出 $37.50")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun modelListNoLongerShowsPriceNoticeOrSourceAction() {
        show(ModelListUiState(listOf(ModelSummary("lab/model", "模型")), isLoading = false))
        compose.onNodeWithContentDescription("数据来源与许可").assertDoesNotExist()
        compose.onNodeWithText("关于这些价格").assertDoesNotExist()
        compose.onNodeWithText("同一模型的不同服务商价格不可直接比较", substring = true)
            .assertDoesNotExist()
    }

    @Test fun emptyCatalogDoesNotShowPriceNotice() {
        show(ModelListUiState(isLoading = false))
        compose.onNodeWithText("同一模型的不同服务商价格不可直接比较", substring = true).assertDoesNotExist()
    }

    @Test fun closedWeightsDoNotShowLicense() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/closed",
            name = "模型",
            openWeights = false,
            license = "proprietary",
        )), isLoading = false))
        compose.onNodeWithText("权重未公开").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("proprietary", substring = true).assertDoesNotExist()
    }

    @Test fun loadingDoesNotLookLikeEmptyResults() {
        show(ModelListUiState())
        compose.onNodeWithContentDescription("正在加载模型目录…").assertIsDisplayed()
        compose.onNodeWithTag("model-skeleton:0").assertIsDisplayed()
        compose.onNodeWithText("暂无符合收录规则的 LLM 模型。").assertDoesNotExist()
    }

    @Test fun successfulEmptyCatalogShowsEmptyState() {
        show(ModelListUiState(isLoading = false))
        compose.onNodeWithText("暂无符合收录规则的 LLM 模型。").assertIsDisplayed()
    }

    @Test fun failureOffersRetry() {
        var retries = 0
        show(ModelListUiState(isLoading = false, error = ModelListError.NETWORK)) { retries++ }
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
    }

    @Test fun restoresExpandedPriceDetails() {
        val restoration = StateRestorationTester(compose)
        val model = ModelSummary(
            id = "lab/tiered",
            name = "有档位的模型",
            price = ModelPriceSummary(
                providerId = "p", providerName = "P", isOfficial = false,
                input = 1.0, output = 2.0,
                tiers = listOf(ModelPriceTierSummary("context", 128_000, input = 3.0, output = 4.0)),
            ),
        )
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(ModelListUiState(listOf(model), isLoading = false))
            }
        }
        compose.onNodeWithContentDescription("展开档位价格").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("收起档位价格").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("上下文 ≥ 128,000 tokens：输入 $3.00 · 输出 $4.00")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun largeFontKeepsLongNameAndCompactTokensReadableWithExactSemantics() {
        val name = "这是一个用于验证大字体换行的 Very Long Model Name 2026"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    ModelListScreen(ModelListUiState(listOf(ModelSummary(
                        id = "lab/long", name = name,
                        contextLimitTokens = Long.MAX_VALUE,
                        outputLimitTokens = 131_072,
                        inputModalities = listOf("text", "image", "future-modality"),
                    )), isLoading = false))
                }
            }
        }
        compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        assertField("context-limit", "9223372036854.8M")
        compose.onNodeWithContentDescription("上下文长度：9,223,372,036,854,775,807 tokens").assertIsDisplayed()
        layouts.clear()
        compose.onNode(hasText("9223372036854.8M") and hasAnyAncestor(hasTestTag("context-limit")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertNoTextClipping("context-limit", layouts.single())
        assertField("output-limit", "131.1K")
        compose.onNodeWithContentDescription("最大输出：131,072 tokens").assertIsDisplayed()
        assertModalityIcon("input-modalities", "future-modality")
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-header"))
        compose.onNodeWithText("Model Atlas").assertIsDisplayed()
    }

    private fun assertPriceChevronPointsDown(pointsDown: Boolean) {
        val pixels = compose.onNodeWithTag("price-chevron", useUnmergedTree = true)
            .performScrollTo().captureToImage().toPixelMap()
        val background = pixels[0, 0]
        val paintedRows = (0 until pixels.height).mapNotNull { y ->
            val paintedColumns = (0 until pixels.width).filter { x ->
                val pixel = pixels[x, y]
                abs(pixel.red - background.red) > 0.1f ||
                    abs(pixel.green - background.green) > 0.1f ||
                    abs(pixel.blue - background.blue) > 0.1f
            }
            if (paintedColumns.isEmpty()) null else paintedColumns.last() - paintedColumns.first()
        }
        assertTrue("Chevron must be painted", paintedRows.isNotEmpty())
        val topSpan = paintedRows.take((paintedRows.size + 1) / 2).max()
        val bottomSpan = paintedRows.takeLast((paintedRows.size + 1) / 2).max()
        // V 形箭头上宽下窄，倒 V 相反；检查实际绘制方向而非仅检查展开状态。
        if (pointsDown) {
            assertTrue("Collapsed chevron must point down ($topSpan > $bottomSpan)", topSpan > bottomSpan)
        } else {
            assertTrue("Expanded chevron must point up ($topSpan < $bottomSpan)", topSpan < bottomSpan)
        }
    }

    private fun assertFlatPriceSection(darkTheme: Boolean) {
        compose.setContent {
            ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                ModelCard(
                    model = ModelSummary(
                        id = "lab/flat-price", name = "模型",
                        price = ModelPriceSummary(
                            providerId = "p", providerName = "P", isOfficial = false,
                            input = 0.15, output = 0.60, cacheRead = 0.003,
                        ),
                    ),
                    priceDetailsExpanded = false,
                    onTogglePriceDetails = {},
                    modifier = Modifier.width(320.dp),
                )
            }
        }
        val section = compose.onNodeWithTag("model-price-section")
        val divider = compose.onNodeWithTag("model-price-divider")
        section.assertIsDisplayed()
        divider.assertIsDisplayed()
        val sectionBounds = section.getUnclippedBoundsInRoot()
        val dividerBounds = divider.getUnclippedBoundsInRoot()
        val titleBounds = compose.onNodeWithText("API 价格").getUnclippedBoundsInRoot()
        assertEquals("Price section must align with the divider", dividerBounds.left.value, sectionBounds.left.value, 0.5f)
        assertEquals("Price title must not retain inner card padding", sectionBounds.left.value, titleBounds.left.value, 0.5f)
        assertTrue("Divider must precede the price section", dividerBounds.bottom < sectionBounds.top)

        // 比较实际绘制背景，避免仅移除 Surface 层级、却遗留独立底色。
        val cardPixels = compose.onNodeWithTag("model:lab/flat-price").captureToImage().toPixelMap()
        val sectionPixels = section.captureToImage().toPixelMap()
        val cardBackground = cardPixels[2, cardPixels.height / 2]
        assertEquals("Prices must share the outer card background", cardBackground,
            sectionPixels[sectionPixels.width - 2, sectionPixels.height / 2])
    }

    private fun assertStatusBarShowsScrolledContent(darkTheme: Boolean, fontScale: Float = 1f) {
        var topInsetPx = 0
        var bottomInsetPx = 0
        compose.setContent {
            val density = LocalDensity.current
            topInsetPx = with(density) { 64.dp.roundToPx() }
            bottomInsetPx = with(density) { 32.dp.roundToPx() }
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                    ModelListScreen(
                        uiState = ModelListUiState(
                            models = List(40) { ModelSummary("lab/model-$it", "模型 $it") },
                            isLoading = false,
                        ),
                        // 固定高刘海与底部安全区，避免测试取决于宿主 Activity 的系统栏配置。
                        windowInsets = WindowInsets(top = topInsetPx, bottom = bottomInsetPx),
                    )
                }
            }
        }
        listOf(
            compose.onNodeWithText("Model Atlas"),
            compose.onNodeWithText("模型目录 · 40 个模型"),
            compose.onNodeWithTag("model:lab/model-0"),
        ).forEach { node ->
            assertTrue("Initial content must avoid the top safe area", node.fetchSemanticsNode().boundsInRoot.top >= topInsetPx)
        }
        val before = compose.onRoot().captureToImage().toPixelMap()
        compose.onNodeWithTag("model-list")
            .performScrollToNode(hasTestTag("model:lab/model-20"))
            .performTouchInput { swipeUp() }
        compose.onNodeWithText("Model Atlas").assertIsNotDisplayed()
        val after = compose.onRoot().captureToImage().toPixelMap()
        // 不仅检查列表的布局边界：验证滚动后，安全区内的像素确实来自滚动内容，
        // 而不是仍被 TopAppBar 的实色背景遮挡。避开最顶部渐变最浓的部分。
        assertTrue("Scrolled content must remain visible through the status bar protection", (topInsetPx / 2 until topInsetPx).any { y ->
            (after.width / 4 until after.width * 3 / 4).any { x -> before[x, y] != after[x, y] }
        })

        compose.onNodeWithTag("model-list").performTouchInput { swipeDown() }
        compose.onNodeWithText("Model Atlas").assertIsNotDisplayed()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-header"))
        compose.onNodeWithText("Model Atlas").assertIsDisplayed()
        compose.onNodeWithText("模型目录 · 40 个模型").assertIsDisplayed()
        compose.onNodeWithTag("model-list")
            .performScrollToNode(hasTestTag("model:lab/model-39"))
            .performTouchInput { swipeUp() }
        val lastModel = compose.onNodeWithTag("model:lab/model-39")
        lastModel.assertIsDisplayed()
        assertTrue("Last content must avoid the bottom safe area", lastModel.fetchSemanticsNode().boundsInRoot.bottom <=
            compose.onNodeWithTag("model-list").fetchSemanticsNode().boundsInRoot.bottom - bottomInsetPx)
    }

    private fun show(state: ModelListUiState, onRetry: () -> Unit = {}) {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelListScreen(state, onRetry = onRetry) } }
    }

    private fun assertNoTextClipping(field: String, layout: TextLayoutResult) {
        // 紧凑 Text 的布局宽度可能小于内部 paragraph 的约束宽度，hasVisualOverflow 会误报；
        // 检查每行实际文字边界、是否省略，以及完整原文是否都已排版，而不是放宽可见性要求。
        assertFalse("$field height overflow", layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            assertTrue("$field line $line clips at start", layout.getLineLeft(line) >= -1f)
            assertTrue("$field line $line clips at end", layout.getLineRight(line) <= layout.size.width + 1f)
            assertFalse("$field line $line is ellipsized", layout.isLineEllipsized(line))
        }
        assertEquals("$field must lay out the full value", layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    private fun assertField(tag: String, text: String) {
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
    }

    private fun assertModalityIcon(tag: String, description: String) {
        compose.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(description) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
