package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsNameLabReleaseDateLimitsAndDirectionalModalities() {
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
        listOf(
            "研发机构：lab", "发布时间：2026-10", "上下文长度：5000000000 tokens", "最大输出：16384 tokens",
            "输入：文本、图片、视频、PDF", "输出：文本、音频、future-modality",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithText("API 服务商：", substring = true).assertDoesNotExist()
        compose.onNodeWithText("本地示例数据", substring = true).assertDoesNotExist()
    }

    @Test fun labLogoUsesBundledAssetForKnownLab() {
        show(ModelListUiState(listOf(
            ModelSummary(id = "openai/gpt-5", name = "有素材模型"),
            ModelSummary(id = "meta/llama-5", name = "另一个有素材模型"),
        ), isLoading = false))
        compose.onAllNodesWithTag(LabLogoTag).assertCountEquals(2)
        compose.onNodeWithTag(LabLogoFallbackTag).assertDoesNotExist()
        compose.onNodeWithText("研发机构：openai").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("研发机构：meta").performScrollTo().assertIsDisplayed()
    }

    @Test fun labLogoFallsBackWhenNoBundledAsset() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "acme/atlas-mini",
            name = "无素材模型",
        )), isLoading = false))
        compose.onNodeWithTag(LabLogoFallbackTag).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LabLogoTag).assertDoesNotExist()
        compose.onNodeWithText("研发机构：acme").assertIsDisplayed()
    }

    @Test fun labLogoFallsBackWhenLabIsMissing() {
        show(ModelListUiState(listOf(ModelSummary("unlinked", "未关联模型")), isLoading = false))
        compose.onNodeWithTag(LabLogoFallbackTag).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(LabLogoTag).assertDoesNotExist()
        compose.onNodeWithText("研发机构：暂无数据").assertIsDisplayed()
    }

    @Test fun missingMetadataIsNotInvented() {
        show(ModelListUiState(listOf(ModelSummary("unlinked", "未关联模型")), isLoading = false))
        listOf(
            "研发机构：暂无数据", "发布时间：暂无数据", "上下文长度：暂无数据", "最大输出：暂无数据",
            "输入：暂无数据", "输出：暂无数据",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
    }

    @Test fun emptyModalitiesAndZeroLimitAreNotTreatedAsMissing() {
        show(ModelListUiState(listOf(ModelSummary(
            id = "lab/model",
            name = "模型",
            outputLimitTokens = 0L,
            inputModalities = emptyList(),
            outputModalities = listOf("text"),
        )), isLoading = false))
        listOf("最大输出：0 tokens", "输入：无", "输出：文本").forEach { text ->
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
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
                id = "lab/closed",
                name = "未公开权重模型",
                openWeights = false,
            ),
        ), isLoading = false))
        listOf(
            "开放权重：权重公开", "许可：Apache-2.0",
            "开放权重：未公开",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
    }

    @Test fun missingOpenWeightsAndLicenseAreNotInvented() {
        show(ModelListUiState(listOf(ModelSummary("unlinked", "未关联模型")), isLoading = false))
        compose.onNodeWithText("开放权重：暂无数据").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("许可：", substring = true).assertDoesNotExist()
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
            "价格（USD / 百万 tokens）",
            "输入：$0.60",
            "输出：$0.15",
            "缓存命中输入：$0.003",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
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
        compose.onNodeWithText("输入：$0.50").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("输出：$1.50").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("OpenRouter").assertDoesNotExist()
    }

    @Test fun missingPriceIsNotInventedAsZero() {
        show(ModelListUiState(listOf(ModelSummary("lab/model", "模型")), isLoading = false))
        compose.onNodeWithText("价格：暂无数据").performScrollTo().assertIsDisplayed()
        // 没有可用报价时不渲染任何价格行；模态的“输入：暂无数据”与此无关。
        compose.onNodeWithText("输入：$", substring = true).assertDoesNotExist()
        compose.onNodeWithText("输出：$", substring = true).assertDoesNotExist()
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
        compose.onNodeWithText("输入：$0.00").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("输出：$0.00").assertIsDisplayed()
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
        // 价格与模态都使用“输入：”前缀，缺失时都显示暂无数据，这里两条都应存在。
        compose.onAllNodesWithText("输入：暂无数据").assertCountEquals(2)
        compose.onNodeWithText("输出：$4.00").performScrollTo().assertIsDisplayed()
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
            "缓存命中输入：$0.50", "缓存写入：$6.25", "推理：$25.00",
            "音频输入：$1.00", "音频输出：$2.00",
        ).forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
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
        compose.onNodeWithText("展开档位价格").assertDoesNotExist()
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
        compose.onNodeWithText("展开档位价格").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("上下文 ≥ 272000 tokens：输入 $10.00 · 输出 $37.50").assertDoesNotExist()

        compose.onNodeWithText("展开档位价格").performScrollTo().performClick()
        compose.onNodeWithText("上下文 ≥ 272000 tokens：输入 $10.00 · 输出 $37.50")
            .performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("收起档位价格").performScrollTo().performClick()
        compose.onNodeWithText("上下文 ≥ 272000 tokens：输入 $10.00 · 输出 $37.50").assertDoesNotExist()
    }

    @Test fun priceComparabilityNoticeIsShownWithModels() {
        show(ModelListUiState(listOf(ModelSummary("lab/model", "模型")), isLoading = false))
        compose.onNodeWithText("同一模型的不同服务商价格不可直接比较", substring = true)
            .performScrollTo().assertIsDisplayed()
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
        compose.onNodeWithText("开放权重：未公开").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("许可：proprietary").assertDoesNotExist()
    }

    @Test fun loadingDoesNotLookLikeEmptyResults() {
        show(ModelListUiState())
        compose.onNodeWithText("正在加载模型目录…").assertIsDisplayed()
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

    @Test fun displaysBundledUpstreamLicense() {
        show(ModelListUiState(isLoading = false))
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithText("Copyright (c) 2025 models.dev", substring = true).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("Copyright (c) 2025 models.dev", substring = true).assertDoesNotExist()
    }

    @Test fun restoresOpenLicenseDialog() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme { ModelListScreen(ModelListUiState(isLoading = false)) }
        }
        compose.onNodeWithText("数据来源与许可").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Copyright (c) 2025 models.dev", substring = true).assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
    }

    private fun show(state: ModelListUiState, onRetry: () -> Unit = {}) {
        compose.setContent { ModelAtlasTheme { ModelListScreen(state, onRetry = onRetry) } }
    }
}
