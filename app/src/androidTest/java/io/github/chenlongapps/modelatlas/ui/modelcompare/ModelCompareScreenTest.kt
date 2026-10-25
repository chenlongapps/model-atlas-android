package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModelCompareScreenTest {
    @get:Rule val compose = createComposeRule()
    private val details = listOf(
        ModelDetail("deepseek/a", "Model A", metadata = ModelDetailMetadata(
            releaseDate = "2026-10", contextLimitTokens = 131_072, outputLimitTokens = 4_294_967_296,
            inputModalities = listOf("text", "image", "future"), outputModalities = emptyList(),
            openWeights = true, reasoning = true, toolCall = false,
        ), license = "A complete license name that must not be truncated", fetchedAtEpochMillis = 0),
        ModelDetail("openai/b", "Model B", metadata = ModelDetailMetadata(
            contextLimitTokens = 400_000, inputModalities = null, outputModalities = listOf("text"),
            openWeights = false, reasoning = null, structuredOutput = true,
        ), fetchedAtEpochMillis = 0),
    )
    private val summaries = details.mapIndexed { index, detail -> ModelSummary(detail.id, detail.name,
        price = if (index == 0) ModelPriceSummary("provider", "Default Provider", false, input = 0.0, output = 0.003,
            tiers = listOf(ModelPriceTierSummary("context", 272_001, input = 0.004, output = 2.0,
                reasoning = 0.0001, inputAudio = 0.5)), offeringId = "selected-service") else null) }
    private val ready = ModelCompareUiState(summaries, CompareReadStatus.CONTENT,
        CompareModelState(details[0].id, CompareReadStatus.CONTENT, details[0]),
        CompareModelState(details[1].id, CompareReadStatus.CONTENT, details[1]))

    @Test fun selectsTwoDifferentModelsThroughSearchAndCanRemoveOrReplace() {
        val state = mutableStateOf(ModelCompareUiState(models = summaries, catalogStatus = CompareReadStatus.CONTENT))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelCompareScreen(state.value, onSelect = { index, id ->
                    val selected = CompareModelState(id, CompareReadStatus.CONTENT, details.first { it.id == id })
                    state.value = if (index == 0) state.value.copy(first = selected) else state.value.copy(second = selected)
                }, onRemove = { index ->
                    state.value = if (index == 0) state.value.copy(first = CompareModelState()) else state.value.copy(second = CompareModelState())
                })
            }
        }
        compose.onNodeWithText("选择两个模型，开始逐项对比").assertIsDisplayed()
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithTag("compare-search").performTextInput("deepseek a")
        compose.onNodeWithTag("compare-option:deepseek/a").performClick()
        compose.onNodeWithText("再选择一个不同的模型，即可开始对比").assertIsDisplayed()
        compose.onNodeWithTag("compare-slot-1").performClick()
        compose.onNodeWithTag("compare-option:deepseek/a").assertIsNotEnabled()
        compose.onNodeWithTag("compare-option:openai/b").performClick()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("基本信息"))
        compose.onNodeWithText("基本信息").assertIsDisplayed()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("模型对比"))
        compose.onNodeWithTag("compare-remove-0").performClick()
        compose.onNodeWithText("再选择一个不同的模型，即可开始对比").assertIsDisplayed()
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithTag("compare-option:openai/b").assertIsNotEnabled()
    }

    @Test fun cancellingPickerDoesNotChangeSelectionAndNoResultsCanBeCleared() {
        var selects = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(ready, onSelect = { _, _ -> selects++ })
        } }
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithTag("compare-search").performTextInput("does-not-exist")
        compose.onNodeWithText("试试其他关键词，或放宽筛选条件。").assertIsDisplayed()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithTag("compare-option:deepseek/a").assertIsDisplayed()
        pressBack() // 先关闭键盘。
        if (compose.onAllNodes(hasText("选择对比模型")).fetchSemanticsNodes().isNotEmpty()) pressBack()
        compose.onNodeWithTag("compare-picker").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, selects) }
    }

    @Test fun pickerPrioritizesFavoritesDuringSearchAndUpdatesWithoutEnablingDuplicateSelection() {
        var selects = 0
        val state = mutableStateOf(ModelCompareUiState(models = summaries, catalogStatus = CompareReadStatus.CONTENT,
            second = ready.second, favoriteIds = setOf("openai/b", "removed/model")))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onSelect = { _, _ -> selects++ })
        } }
        compose.onNodeWithTag("compare-slot-0").performClick()
        fun assertBefore(first: String, second: String) {
            val firstBounds = compose.onNodeWithTag("compare-option:$first").fetchSemanticsNode().boundsInRoot
            val secondBounds = compose.onNodeWithTag("compare-option:$second").fetchSemanticsNode().boundsInRoot
            assertTrue(firstBounds.bottom <= secondBounds.top)
        }
        assertBefore("openai/b", "deepseek/a")
        compose.onNodeWithTag("compare-option:openai/b").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("compare-option:removed/model").assertDoesNotExist()
        compose.onNodeWithTag("compare-search").performTextInput("Model")
        assertBefore("openai/b", "deepseek/a")
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithTag("compare-search").performTextInput("deepseek")
        compose.onNodeWithTag("compare-option:deepseek/a").assertIsDisplayed()
        compose.onNodeWithTag("compare-option:openai/b").assertDoesNotExist()
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.runOnIdle { state.value = state.value.copy(favoriteIds = setOf("deepseek/a")) }
        assertBefore("deepseek/a", "openai/b")
        compose.onNodeWithTag("compare-option:openai/b").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, selects) }
    }

    @Test fun showsExactParameterSemanticsUnknownCollectionsAndDefaultQuoteOnly() {
        show()
        scrollToDescription("Model A，上下文长度：131,072 tokens")
        scrollToDescription("Model A，最大输出：4,294,967,296 tokens")
        scrollToDescription("Model A，输入模态：文本、图片、future")
        scrollToDescription("Model A，输出模态：无")
        scrollToDescription("Model B，输入模态：暂无数据")
        scrollToDescription("Model A，报价服务商：Default Provider")
        toggleDetail("service-id")
        scrollToDescription("Model A，报价服务 ID：selected-service")
        scrollToDescription("Model A，输入：$0.00")
        scrollToDescription("Model A，输出：$0.003")
        scrollToDescription("Model B，输入：暂无数据")
        scrollToDescription("Model A，工具调用：不支持")
        scrollToDescription("Model A，推理：支持")
        scrollToDescription("Model B，推理：暂无数据")
        toggleDetail("license")
        scrollToDescription("Model A，模型许可：A complete license name that must not be truncated")
    }

    @Test fun phoneUsesFullWidthValuePairsAndEqualHeightAlignedSelectionCards() {
        val first = details[0].copy(name = "Kimi K3.1 Pro")
        val second = details[1].copy(name = "DeepSeek V4.1 Flash 1017")
        showAtWidth(400.dp, ready.copy(
            first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        assertSelectionCardsAligned()
        assertTrue(selectionTextLayout("compare-name-1").lineCount > selectionTextLayout("compare-name-0").lineCount)
        compose.onNodeWithText("VS").assertDoesNotExist()
        compose.onNodeWithText("开放权重").assertDoesNotExist()
        compose.onNodeWithText("权重未公开").assertDoesNotExist()
        scrollToDescription("Kimi K3.1 Pro，发布时间：2026-10")
        val label = compose.onNodeWithText("发布时间").fetchSemanticsNode().boundsInRoot
        val firstValue = compose.onNodeWithContentDescription("Kimi K3.1 Pro，发布时间：2026-10").fetchSemanticsNode().boundsInRoot
        val secondValue = compose.onNodeWithContentDescription("DeepSeek V4.1 Flash 1017，发布时间：暂无数据").fetchSemanticsNode().boundsInRoot
        assertTrue(label.bottom < firstValue.top)
        assertEquals(firstValue.top, secondValue.top, 1f)
        assertEquals(firstValue.width, secondValue.width, 1f)
        assertTrue(firstValue.right < secondValue.left)
        assertEquals(label.left, firstValue.left, 1f)
        compose.onAllNodesWithTag("compare-column-0")[0].assertTextEquals(first.name)
        compose.onNodeWithText("模型名称").assertDoesNotExist()
    }

    @Test fun selectionCardsGrowBeyondTwoLinesAndShrinkAfterReplacement() {
        val longName = "DeepSeek V4.1 Flash 1017 Extended Context Reasoning Preview"
        val state = mutableStateOf(ready.copy(second = ready.second.copy(detail = details[1].copy(name = longName))))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            Box(Modifier.width(360.dp)) { ModelCompareScreen(state.value) }
        } }
        assertSelectionCardsAligned()
        assertTrue(selectionTextLayout("compare-name-1").lineCount > 2)
        val longHeight = compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot.height
        compose.runOnIdle { state.value = ready }
        assertSelectionCardsAligned()
        val shortHeight = compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot.height
        assertTrue(shortHeight < longHeight)
        assertEquals(1, selectionTextLayout("compare-name-1").lineCount)
        compose.runOnIdle { state.value = ready.copy(first = CompareModelState()) }
        val empty = compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot
        val selected = compose.onNodeWithTag("compare-slot-1").fetchSemanticsNode().boundsInRoot
        assertEquals(empty.top, selected.top, 1f)
        assertEquals(empty.bottom, selected.bottom, 1f)
    }

    @Test fun sideBySideLargeFontRtlKeepsLongNamesAndLabsAligned() {
        val second = details[1].copy(id = "A very long research organization/b", name = "DeepSeek V4.1 Flash 1017 Preview")
        showAtWidth(600.dp, ready.copy(second = CompareModelState(second.id, CompareReadStatus.CONTENT, second)),
            fontScale = 1.5f, direction = LayoutDirection.Rtl, darkTheme = true)
        assertSelectionCardsAligned()
        assertTrue(selectionTextLayout("compare-name-1").lineCount > 1)
        assertTrue(selectionTextLayout("compare-lab-1").lineCount > 1)
        val first = compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot
        val other = compose.onNodeWithTag("compare-slot-1").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left > other.right)
    }

    @Test fun wideLayoutKeepsFieldColumnAndAlignedModelHeaders() {
        showAtWidth(840.dp)
        scrollToDescription("Model A，发布时间：2026-10")
        val label = compose.onNodeWithText("发布时间").fetchSemanticsNode().boundsInRoot
        val firstValue = compose.onNodeWithContentDescription("Model A，发布时间：2026-10").fetchSemanticsNode().boundsInRoot
        val secondValue = compose.onNodeWithContentDescription("Model B，发布时间：暂无数据").fetchSemanticsNode().boundsInRoot
        assertEquals(label.top, firstValue.top, 1f)
        assertEquals(firstValue.top, secondValue.top, 1f)
        assertTrue(label.right < firstValue.left)
        assertTrue(firstValue.right < secondValue.left)
        val firstHeader = compose.onAllNodesWithTag("compare-column-0")[0].fetchSemanticsNode().boundsInRoot
        val secondHeader = compose.onAllNodesWithTag("compare-column-1")[0].fetchSemanticsNode().boundsInRoot
        assertEquals(firstHeader.left, firstValue.left, 1f)
        assertEquals(secondHeader.left, secondValue.left, 1f)
    }

    @Test fun detailDisclosuresDefaultCollapsedSurviveRecyclingAndRestoreButResetOnReplacement() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(ready)
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        val descriptions = listOf(
            "model-id" to "Model A，模型 ID：deepseek/a",
            "service-id" to "Model A，报价服务 ID：selected-service",
            "license" to "Model A，模型许可：A complete license name that must not be truncated",
        )
        descriptions.forEach { (key, description) ->
            compose.onNodeWithTag("compare-list").performScrollToNode(hasText(when (key) {
                "model-id" -> "查看模型 ID"
                "service-id" -> "查看报价服务 ID"
                else -> "查看完整许可"
            }))
            compose.onNodeWithContentDescription(description).assertDoesNotExist()
            compose.onNodeWithTag("compare-$key-toggle").performClick()
            scrollToDescription(description)
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = ready.copy(first = CompareModelState("deepseek/a")) }
        compose.runOnIdle { state.value = ready }
        descriptions.forEach { (_, description) -> scrollToDescription(description) }
        toggleDetail("license")
        compose.onNodeWithContentDescription(descriptions.last().second).assertDoesNotExist()
        toggleDetail("license")
        compose.runOnIdle {
            val replacement = details[0].copy(id = "deepseek/replacement")
            state.value = ready.copy(models = summaries + ModelSummary(replacement.id, replacement.name),
                first = CompareModelState(replacement.id, CompareReadStatus.CONTENT, replacement))
        }
        descriptions.forEach { (key, _) ->
            compose.onNodeWithTag("compare-list").performScrollToNode(hasText(when (key) {
                "model-id" -> "查看模型 ID"
                "service-id" -> "查看报价服务 ID"
                else -> "查看完整许可"
            }))
            compose.onNodeWithTag("compare-$key-toggle").assertIsDisplayed()
        }
    }

    @Test fun loadingMissingAndErrorNeverShowOldComparisonAndRetryIsLocalCallback() {
        var retries = 0
        val state = mutableStateOf(ready.copy(first = CompareModelState("deepseek/a", CompareReadStatus.NOT_FOUND)))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onRetry = { retries++ })
        } }
        compose.onNodeWithText("此模型已被移除或不再收录，请替换或移除。").assertIsDisplayed()
        compose.onNodeWithText("基本信息").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(first = CompareModelState("deepseek/a", CompareReadStatus.ERROR)) }
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.runOnIdle { state.value = state.value.copy(first = CompareModelState("deepseek/a")) }
        compose.onNodeWithText("正在读取本地模型数据…").assertIsDisplayed()
        compose.onNodeWithText("基本信息").assertDoesNotExist()
    }

    @Test fun tierExpansionSurvivesRecyclingAndAsyncStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(ready)
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("上下文及其他条件档位"))
        compose.onNodeWithTag("compare-tiers-toggle").performClick()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("上下文档位门槛：272,001 tokens"))
        compose.onNodeWithText("上下文档位门槛：272,001 tokens").assertIsDisplayed()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("推理：$0.0001"))
        compose.onNodeWithText("推理：$0.0001").assertIsDisplayed()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("权重与许可"))
        val before = compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(before, compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { state.value = ready.copy(first = CompareModelState("deepseek/a"), second = CompareModelState("openai/b")) }
        // 详情先到、目录／默认报价稍后到，也不能提前放置缺少档位的短表格。
        compose.runOnIdle { state.value = ready.copy(models = emptyList(), catalogStatus = CompareReadStatus.LOADING) }
        compose.onNodeWithText("基本信息").assertDoesNotExist()
        compose.runOnIdle { state.value = ready }
        assertEquals(before, compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("上下文档位门槛：272,001 tokens"))
        compose.onNodeWithText("上下文档位门槛：272,001 tokens").assertIsDisplayed()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("上下文及其他条件档位"))
        compose.onNodeWithTag("compare-tiers-toggle").performClick()
        compose.onNodeWithText("上下文档位门槛：272,001 tokens").assertDoesNotExist()
    }

    @Test fun compareTabKeepsBottomNavigationAndReadingPositionAcrossTabs() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelAtlasScaffold(compareContent = { padding, visible ->
                ModelCompareScreen(ready, bottomOverlayPadding = padding, isVisible = visible)
            }) { _, _, _ -> }
        } }
        compose.onNodeWithTag("nav-compare").performClick().assertIsSelected()
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertDoesNotExist()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("权重与许可"))
        val before = compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-compare").performClick().assertIsSelected()
        assertEquals(before, compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun narrowDarkRtlDoubleFontKeepsExactValuesAndLongLicenseReadable() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    Box(Modifier.width(320.dp)) { ModelCompareScreen(ready) }
                }
            }
        }
        val first = compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("compare-slot-1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.left, second.left, 1f)
        assertTrue(first.bottom < second.top)
        scrollToDescription("Model A，输出：$0.003")
        toggleDetail("license")
        scrollToDescription("Model A，模型许可：A complete license name that must not be truncated")
        val text = "A complete license name that must not be truncated"
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.didOverflowHeight)
        repeat(layout.lineCount) {
            assertTrue(layout.getLineLeft(it) >= -1f)
            assertTrue(layout.getLineRight(it) <= layout.size.width + 1f)
            assertFalse(layout.isLineEllipsized(it))
        }
        assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
    }

    @Test fun emptyCatalogAndReadFailureOfferAccuratePickerGuidance() {
        var retries = 0
        val state = mutableStateOf(ModelCompareUiState(catalogStatus = CompareReadStatus.CONTENT))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onRetryCatalog = { retries++ })
        } }
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithText("本地暂无可选模型，请先到“模型”页加载目录。").assertIsDisplayed()
        pressBack()
        compose.runOnIdle { state.value = state.value.copy(catalogStatus = CompareReadStatus.ERROR) }
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    private fun show() {
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(ready) } }
    }

    private fun showAtWidth(
        width: Dp, state: ModelCompareUiState = ready, fontScale: Float = 1f,
        direction: LayoutDirection = LayoutDirection.Ltr, darkTheme: Boolean = false,
    ) {
        compose.setContent {
            BoxWithConstraints {
                val density = LocalDensity.current
                val scale = minOf(1f, maxWidth / width)
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, fontScale),
                    LocalLayoutDirection provides direction) {
                    ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = false) {
                        Box(Modifier.width(width)) { ModelCompareScreen(state) }
                    }
                }
            }
        }
    }

    private fun assertSelectionCardsAligned() {
        val first = compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("compare-slot-1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertEquals(first.bottom, second.bottom, 1f)
        assertEquals(first.width, second.width, 1f)
        listOf("name", "lab", "change").forEach { field ->
            val left = compose.onNodeWithTag("compare-$field-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("compare-$field-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("$field top", left.top, right.top, 1f)
            assertEquals("$field bottom", left.bottom, right.bottom, 1f)
            selectionTextLayout("compare-$field-0")
            selectionTextLayout("compare-$field-1")
        }
    }

    private fun selectionTextLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.didOverflowHeight)
        repeat(layout.lineCount) {
            assertFalse(layout.isLineEllipsized(it))
            assertTrue(layout.getLineLeft(it) >= -1f)
            assertTrue(layout.getLineRight(it) <= layout.size.width + 1f)
        }
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        return layout
    }

    private fun toggleDetail(key: String) {
        compose.onNodeWithTag("compare-list").performScrollToNode(hasTestTag("compare-$key-toggle"))
        compose.onNodeWithTag("compare-$key-toggle").performClick()
    }

    private fun scrollToDescription(description: String) {
        compose.onNodeWithTag("compare-list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
    }
}
