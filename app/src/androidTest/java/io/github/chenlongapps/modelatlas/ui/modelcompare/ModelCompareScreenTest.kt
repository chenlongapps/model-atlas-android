package io.github.chenlongapps.modelatlas.ui.modelcompare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.testing.realAtlasModalityFixture
import io.github.chenlongapps.modelatlas.testing.saveRegressionScreenshot
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModelCompareScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actualAtlasMimeFormatsUseTheSameCategoriesInComparison() {
        val fixture = realAtlasModalityFixture()
        val state = ready.copy(models = listOf(fixture.summary, summaries[1]),
            first = CompareModelState(fixture.detail.id, CompareReadStatus.CONTENT, fixture.detail))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state) } }
        scrollToDescription("${fixture.detail.name}，输入模态：文本、图片、音频、视频、PDF")
        scrollToDescription("${fixture.detail.name}，输出模态：文本")
        fixture.rawInputs.filter { '/' in it }.forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }
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
            cacheRead = 0.001, cacheWrite = 0.002, reasoning = 0.006, inputAudio = 0.4, outputAudio = 0.8,
            tiers = listOf(ModelPriceTierSummary("context", 272_001, input = 0.004, output = 2.0,
                reasoning = 0.0001, inputAudio = 0.5)), offeringId = "selected-service") else null) }
    private val ready = ModelCompareUiState(summaries, CompareReadStatus.CONTENT,
        CompareModelState(details[0].id, CompareReadStatus.CONTENT, details[0]),
        CompareModelState(details[1].id, CompareReadStatus.CONTENT, details[1]))
    private val assessment = ModelBenchmark("Example Benchmark", 0.0, benchmarkId = "assessment", version = "1",
        metric = "accuracy", unit = "%", toolsEnabled = false, dataset = "full", harness = "runner 1",
        source = "https://example.org/results", date = "2026-11-01", higherIsBetter = true)

    @Test fun showsOnlyCommonHighestEffortScoresWithoutExposingEffortOrParsingScoreText() {
        val first = details[0].copy(benchmarks = listOf(assessment,
            assessment.copy(score = 98.0, reasoningEffort = "Low"),
            assessment.copy(score = 78.4325, reasoningEffort = "High"),
            assessment.copy(benchmarkId = "solo", name = "Single Benchmark", score = 83.0)))
        val longName = "Model B Long Context Reasoning Preview"
        val second = details[1].copy(name = longName,
            benchmarks = listOf(assessment.copy(score = null, scoreText = "74.5 ± 1.3", reasoningEffort = "Max")))
        showAtWidth(400.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        compose.waitForIdle()
        saveRegressionScreenshot("compare-overview")
        val list = compose.onNodeWithTag("compare-list")
        fun indexOf(key: Any) = list.fetchSemanticsNode().config[SemanticsProperties.IndexForKey](key)
        // 评测分数在末尾，逐段滚动取绝对索引，避免未组合的分区取不到。
        val sectionIndices = listOf("specs" to "关键规格", "prices" to "API 价格", "basic" to "基本信息",
            "capabilities" to "能力特性", "weights" to "权重与许可", "benchmark-heading" to "评测分数").map { (key, heading) ->
            list.performScrollToNode(hasText(heading))
            indexOf(key).also { assertTrue("$key 应出现在列表中", it >= 0) }
        }
        assertTrue(sectionIndices.zipWithNext().all { (before, after) -> before < after })
        val firstScore = "Model A，Example Benchmark，分数：78.4 %"
        val secondScore = "$longName，Example Benchmark，分数：74.5 ± 1.3 %"
        scrollToDescription(firstScore)
        val left = compose.onNodeWithContentDescription(firstScore).fetchSemanticsNode().boundsInRoot
        val right = compose.onNodeWithContentDescription(secondScore).fetchSemanticsNode().boundsInRoot
        assertTrue(left.bottom < right.top)
        assertEquals(left.left, right.left, 1f)
        compose.onNodeWithText("思考档位", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("compare-benchmark-track-0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("compare-benchmark-track-1", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("98.0 %", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Single Benchmark").assertDoesNotExist()
        val key = compareBenchmarkGroups(first.benchmarks, second.benchmarks).single().key
        assertEquals(-1, indexOf(key.copy(id = "solo").itemKey))
        compose.onNode(hasClickAction() and (hasTestTag(key.itemKey) or hasAnyAncestor(hasTestTag(key.itemKey))))
            .assertDoesNotExist()
    }

    @Test fun showsBenchmarkLogoAndStacksProportionalBarsIncludingInRtl() {
        val terminal = assessment.copy(name = "Terminal-Bench", benchmarkId = "terminal-bench-v4", score = 60.0)
        val first = details[0].copy(benchmarks = listOf(terminal))
        val second = details[1].copy(benchmarks = listOf(terminal.copy(score = 75.0)))
        showAtWidth(400.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)),
            direction = LayoutDirection.Rtl)
        scrollToDescription("Model B，Terminal-Bench，分数：75.0 %")
        compose.onNodeWithTag("benchmark-logo:terminal-bench-v4").assertIsDisplayed()
        val tracks = (0..1).map { compose.onNodeWithTag("compare-benchmark-track-$it", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot }
        val fills = (0..1).map { compose.onNodeWithTag("compare-benchmark-fill-$it", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot }
        assertTrue(tracks[0].bottom < tracks[1].top)
        assertEquals(tracks[0].width, tracks[1].width, 1f)
        tracks.zip(fills).forEach { (track, fill) -> assertEquals(track.right, fill.right, 1f) }
        assertEquals(tracks[0].width * 0.60f, fills[0].width, 1f)
        assertEquals(tracks[1].width * 0.75f, fills[1].width, 1f)
        saveRegressionScreenshot("compare-benchmark-bars-rtl")
    }

    @Test fun pointScoresKeepTheirUnitsAndBarsWithoutShowingTheScale() {
        val points = assessment.copy(unit = "points", score = 60.0)
        val first = details[0].copy(benchmarks = listOf(points))
        val second = details[1].copy(benchmarks = listOf(points.copy(score = 64.0)))
        showAtWidth(400.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        scrollToDescription("Model B，Example Benchmark，分数：64.0 points")
        compose.onNodeWithContentDescription("Model A，Example Benchmark，分数：60.0 points").assertIsDisplayed()
        (0..1).forEach { slot ->
            val track = compose.onNodeWithTag("compare-benchmark-track-$slot", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val fill = compose.onNodeWithTag("compare-benchmark-fill-$slot", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(track.width * (if (slot == 0) 60f else 64f) / 80f, fill.width, 1f)
        }
        compose.onNodeWithText("刻度", substring = true, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun incompleteConfigurationsAndMissingScoresShowOnlyEmptyState() {
        val incomplete = assessment.copy(harness = null)
        val first = details[0].copy(benchmarks = listOf(incomplete))
        val second = details[1].copy(benchmarks = listOf(incomplete.copy(score = null)))
        showAtWidth(400.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        scrollToText("暂无可对照的成绩")
        compose.onNodeWithContentDescription("查看评测说明").assertDoesNotExist()
        compose.onNodeWithText("Example Benchmark").assertDoesNotExist()
    }

    @Test fun referenceScoresShowWithoutBadgesOrVersionsAndKeepConfigurationInDialog() {
        val claude = assessment.copy(name = "Terminal-Bench", benchmarkId = "terminal-bench-v4",
            version = "4.0.0", benchmarkDefinitionVersion = "4.0.0", score = 59.5959595959596,
            reasoningEffort = "Max", source = "https://artificialanalysis.ai/models/claude-fable-5-2",
            variant = "Default Fallback")
        val gpt = claude.copy(version = "4.0", score = 64.1414141414141, reasoningEffort = "max",
            source = "https://artificialanalysis.ai/models/gpt-6-2-astra", variant = null)
        val first = details[0].copy(name = "Claude Fable 5.2", benchmarks = listOf(claude, assessment))
        val second = details[1].copy(name = "GPT-6.2 Astra", benchmarks = listOf(gpt, assessment))
        showAtWidth(400.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        val info = "查看评测说明"
        val notice = "下列评测的运行条件不同或尚未确认一致。"
        val fallback = "Claude Fable 5.2：Default Fallback"
        scrollToDescription(info)
        compose.onNodeWithText(notice).assertDoesNotExist()
        compose.onNodeWithText("暂无可对照的成绩").assertDoesNotExist()
        scrollToDescription("Claude Fable 5.2，Example Benchmark，分数：0.0 %")
        val reference = "Claude Fable 5.2，Terminal-Bench，分数：59.6 %"
        scrollToDescription(reference)
        compose.onNodeWithContentDescription("GPT-6.2 Astra，Terminal-Bench，分数：64.1 %")
            .assertIsDisplayed()
        listOf("Default Fallback", "仅供参考", "思考档位", "版本 4.0").forEach {
            compose.onNodeWithText(it, substring = true, useUnmergedTree = true).assertDoesNotExist()
        }
        saveRegressionScreenshot("compare-benchmark-bars")
        scrollToDescription(info)
        val before = compose.onNodeWithContentDescription(info).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(info).performClick()
        compose.onNodeWithText("评测说明").assertIsDisplayed()
        compose.onNodeWithText(notice).assertIsDisplayed()
        compose.onNodeWithText(fallback).assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText(notice).assertDoesNotExist()
        compose.onNodeWithText(fallback).assertDoesNotExist()
        assertEquals(before, compose.onNodeWithContentDescription(info).fetchSemanticsNode().boundsInRoot)
        scrollToDescription(reference)
    }

    @Test fun changingModelClearsOldScoresAndOnlyShowsNewCommonScores() {
        val first = details[0].copy(benchmarks = listOf(assessment))
        val second = details[1].copy(benchmarks = listOf(assessment.copy(score = 80.0)))
        val state = mutableStateOf(ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        val oldScore = "Model B，Example Benchmark，分数：80.0 %"
        scrollToDescription(oldScore)
        compose.runOnIdle { state.value = state.value.copy(second = CompareModelState("new/model")) }
        compose.onNodeWithContentDescription(oldScore).assertDoesNotExist()
        val replacement = second.copy(id = "new/model", name = "Replacement",
            benchmarks = listOf(assessment.copy(score = 91.0), assessment.copy(score = 25.0, unit = "points")))
        compose.runOnIdle {
            state.value = state.value.copy(second = CompareModelState(replacement.id, CompareReadStatus.CONTENT, replacement))
        }
        scrollToDescription("Replacement，Example Benchmark，分数：91.0 %")
        compose.onNodeWithContentDescription(oldScore).assertDoesNotExist()
        compose.onNodeWithContentDescription("Replacement，Example Benchmark，分数：25.0 points").assertDoesNotExist()
    }

    @Test fun narrowLargeFontStacksBenchmarkScoresWithModelNames() {
        val first = details[0].copy(benchmarks = listOf(assessment))
        val second = details[1].copy(benchmarks = listOf(assessment.copy(score = null, scoreText = "899 ± 18")))
        showAtWidth(320.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)),
            fontScale = 2f, direction = LayoutDirection.Rtl, darkTheme = true)
        val firstScore = "Model A，Example Benchmark，分数：0.0 %"
        val secondScore = "Model B，Example Benchmark，分数：899 ± 18 %"
        scrollToDescription(secondScore)
        val start = compose.onNodeWithContentDescription(firstScore).fetchSemanticsNode().boundsInRoot
        val end = compose.onNodeWithContentDescription(secondScore).fetchSemanticsNode().boundsInRoot
        assertTrue(start.bottom < end.top)
        assertEquals(start.right, end.right, 1f)
        compose.onNode(hasText("Model B") and hasAnyAncestor(hasContentDescription(secondScore)),
            useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("899 ± 18 %", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val scoreLayout = layouts.single()
        assertTrue(scoreLayout.getBoundingBox(0).left < scoreLayout.getBoundingBox(6).left)
        saveRegressionScreenshot("compare-benchmark-narrow-dark-rtl")
    }

    @Test fun restoringSelectionDoesNotShowEmptySlotsAndOffersRetryOnReadFailure() {
        val state = mutableStateOf(ModelCompareUiState(selectionStatus = CompareReadStatus.LOADING))
        var retries = 0
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onRetrySelectionLoad = { retries++ })
        } }
        compose.onNodeWithText("正在恢复上次选择…").assertIsDisplayed()
        compose.onNodeWithTag("compare-slot-0").assertDoesNotExist()
        compose.onNodeWithTag("compare-slot-1").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(selectionStatus = CompareReadStatus.ERROR) }
        compose.onNodeWithText("无法读取上次的选择").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            state.value = ready
        }
        compose.onNodeWithTag("compare-name-0", useUnmergedTree = true).assertTextEquals("Model A")
        compose.onNodeWithTag("compare-name-1", useUnmergedTree = true).assertTextEquals("Model B")
    }

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
        compose.onNodeWithText("选择模型 1").assertIsDisplayed()
        compose.onNodeWithText("选择模型 2").assertIsDisplayed()
        val index = compose.onNodeWithTag("compare-list").fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        assertEquals(-1, index("guidance"))
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithTag("compare-remove").assertDoesNotExist()
        compose.onNodeWithTag("compare-search").performTextInput("deepseek a")
        compose.onNodeWithTag("compare-option:deepseek/a").performClick()
        compose.onNodeWithText("选择模型 2").assertIsDisplayed()
        compose.onNodeWithText("评测分数").assertDoesNotExist()
        compose.onNodeWithTag("compare-slot-1").performClick()
        compose.onNodeWithTag("compare-option:deepseek/a").assertIsNotEnabled()
        compose.onNodeWithTag("compare-option:openai/b").performClick()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("评测分数"))
        compose.onNodeWithText("评测分数").assertIsDisplayed()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("模型对比"))
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithTag("compare-remove").performClick()
        compose.onNodeWithText("选择模型 1").assertIsDisplayed()
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithTag("compare-option:openai/b").assertIsNotEnabled()
    }

    @Test fun emptyPartialAndReadySelectionsKeepTheirExpandedPositionsForEitherSlot() {
        val empty = ready.copy(first = CompareModelState(), second = CompareModelState())
        val state = mutableStateOf(empty)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }

        fun bounds(tag: String, index: Int) = compose.onNodeWithTag("compare-$tag-$index", useUnmergedTree = true)
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val slots = List(2) { bounds("slot", it) }
        val marks = List(2) { bounds("add", it) }
        assertEquals(slots[0].top, slots[1].top, 1f)
        assertEquals(slots[0].height, slots[1].height, 1f)
        assertEquals(slots[0].width, slots[1].width, 1f)
        saveRegressionScreenshot("compare-selection-empty")

        repeat(2) { selected ->
            val other = 1 - selected
            val partial = if (selected == 0) empty.copy(first = ready.first) else empty.copy(second = ready.second)
            compose.runOnIdle { state.value = partial }
            assertEquals(marks[other], bounds("add", other))
            assertEquals(marks[selected], bounds("logo", selected))
            val selectedName = bounds("name", selected)
            val selectedLab = bounds("lab", selected)
            assertEquals(slots, List(2) { bounds("slot", it) })
            compose.onNodeWithText("关键规格").assertDoesNotExist()
            if (selected == 0) saveRegressionScreenshot("compare-selection-partial")

            val pending = CompareModelState(ready.slot(other).modelId)
            compose.runOnIdle {
                state.value = if (other == 0) partial.copy(first = pending) else partial.copy(second = pending)
            }
            compose.onNodeWithText("正在读取本地模型数据…").assertIsDisplayed()
            compose.onNodeWithText("关键规格").assertDoesNotExist()
            assertEquals(slots, List(2) { bounds("slot", it) })
            assertEquals(selectedName, bounds("name", selected))
            assertEquals(selectedLab, bounds("lab", selected))

            compose.runOnIdle { state.value = ready }
            compose.onNodeWithText("关键规格").assertIsDisplayed()
            assertEquals(slots, List(2) { bounds("slot", it) })
            assertEquals(selectedName, bounds("name", selected))
            assertEquals(selectedLab, bounds("lab", selected))

            compose.runOnIdle { state.value = partial }
            assertEquals(marks[other], bounds("add", other))
            assertEquals(selectedName, bounds("name", selected))
            compose.onNodeWithText("关键规格").assertDoesNotExist()
            compose.runOnIdle { state.value = empty }
        }
    }

    @Test fun stackedPartialSelectionKeepsBothSlotsUsableWithLargeRtlText() {
        val state = ready.copy(first = CompareModelState(ready.first.modelId, CompareReadStatus.ERROR), second = CompareModelState())
        showAtWidth(320.dp, state, fontScale = 2f, direction = LayoutDirection.Rtl, darkTheme = true)
        val first = compose.onNodeWithTag("compare-slot-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("compare-slot-1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(first.bottom < second.top)
        assertEquals(first.width, second.width, 1f)
        selectionTextLayout("compare-name-1")
        val error = "模型 1：无法读取本地模型详情，请重试。"
        scrollToText(error)
        val statusLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(error).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(statusLayouts) }
        assertEquals(ResolvedTextDirection.Ltr, statusLayouts.single().getParagraphDirection(0))
        compose.onNodeWithText("重试").assertIsDisplayed()
        saveRegressionScreenshot("compare-selection-partial-narrow-dark-rtl")
        compose.onNodeWithTag("compare-slot-1").performClick()
        compose.onNodeWithTag("compare-search").assertIsDisplayed()
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
            val nodes = compose.onAllNodes(hasTestTag("compare-option:$first") or hasTestTag("compare-option:$second"))
                .fetchSemanticsNodes()
            compose.runOnIdle {
                // IME 会移动整个面板；同一个 UI 帧读取两项，不能跨帧比较两套窗口坐标。
                val bounds = nodes.associate { it.config[SemanticsProperties.TestTag] to it.boundsInRoot }
                assertEquals(2, bounds.size)
                assertTrue("收藏候选必须在前：$bounds", bounds.getValue("compare-option:$first").bottom <=
                    bounds.getValue("compare-option:$second").top)
            }
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

    @Test fun showsExactSpecsAndAllDefaultQuoteFieldsWithoutServiceIdOrTiers() {
        show()
        scrollToDescription("Model A，上下文长度：131,072 tokens")
        scrollToDescription("Model A，最大输出：4,294,967,296 tokens")
        scrollToDescription("Model A，输入模态：文本、图片、future")
        scrollToDescription("Model A，输出模态：无")
        scrollToDescription("Model B，输入模态：暂无数据")
        scrollToDescription("Model A，报价服务商：Default Provider")
        scrollToDescription("Model A，输入：$0.00")
        scrollToDescription("Model A，输出：$0.003")
        scrollToDescription("Model B，输入：暂无数据")
        scrollToDescription("Model A，缓存命中输入：$0.001")
        scrollToDescription("Model A，缓存写入：$0.002")
        scrollToDescription("Model A，推理单价：$0.006")
        scrollToDescription("Model A，音频输入：$0.40")
        scrollToDescription("Model A，音频输出：$0.80")
        scrollToDescription("Model B，缓存命中输入：暂无数据")
        val index = compose.onNodeWithTag("compare-list").fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        listOf("service-id", "tiers", "more").forEach { assertEquals(-1, index(it)) }
        listOf("查看详细报价", "报价服务 ID", "selected-service", "上下文及其他条件档位", "上下文档位门槛：272,001 tokens")
            .forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist() }
    }

    @Test fun differencesToggleKeepsItsAnchorAndMissingValuesAndRestoresItsState() {
        val first = details[0].copy(metadata = details[0].metadata.copy(
            contextLimitTokens = 131_070, inputLimitTokens = 1000, openWeights = false,
        ))
        val second = details[1].copy(metadata = details[1].metadata.copy(
            contextLimitTokens = 131_072, inputLimitTokens = 1000,
        ))
        val state = ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state) } }
        val filter = compose.onNodeWithTag("compare-differences")
        compose.onNodeWithTag("compare-list").performScrollToNode(hasTestTag("compare-differences"))
        filter.assertIsOff()
        val before = filter.fetchSemanticsNode().boundsInRoot.top
        filter.performClick().assertIsOn()
        assertEquals(before, filter.fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("最大输入").assertDoesNotExist()
        compose.onNodeWithText("权重状态").assertDoesNotExist()
        compose.onNodeWithText("131,070", useUnmergedTree = true).assertIsDisplayed()
        scrollToDescription("Model B，最大输出：暂无数据")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("compare-list").performScrollToNode(hasTestTag("compare-differences"))
        filter.assertIsOn().performClick().assertIsOff()
        scrollToDescription("Model A，最大输入：1,000 tokens")
    }

    @Test fun differencesToggleAlsoFiltersModelInfoSections() {
        val first = details[0].copy(metadata = details[0].metadata.copy(
            releaseDate = "2026-10", knowledgeCutoff = "2025-06", attachment = true, toolCall = true,
            structuredOutput = false, temperature = true,
        ), license = "MIT")
        val second = details[1].copy(metadata = details[1].metadata.copy(
            releaseDate = "2026-09", knowledgeCutoff = "2025-06", attachment = true, toolCall = false,
            structuredOutput = false, temperature = true,
        ), license = "MIT")
        showAtWidth(400.dp, ready.copy(first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        scrollToDescription("Model A，文件附件：支持")
        scrollToDescription("Model A，知识截止时间：2025-06")
        compose.onNodeWithTag("compare-list").performScrollToNode(hasTestTag("compare-differences"))
        compose.onNodeWithTag("compare-differences").performClick().assertIsOn()

        scrollToText("基本信息")
        compose.onNodeWithText("知识截止时间").assertDoesNotExist()
        scrollToDescription("Model A，发布时间：2026-10")
        assertValuePair("Model A，发布时间：2026-10", "Model B，发布时间：2026-09")

        scrollToText("能力特性")
        listOf("文件附件", "结构化输出", "温度调节").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        scrollToDescription("Model A，工具调用：支持")
        scrollToDescription("Model B，工具调用：不支持")
        scrollToDescription("Model B，推理：暂无数据")
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("评测分数"))
        assertEquals(-1, compose.onNodeWithTag("compare-list").fetchSemanticsNode()
            .config[SemanticsProperties.IndexForKey]("weights"))
    }

    @Test fun matchingSpecsHaveAnEmptyDifferencesState() {
        val second = details[1].copy(metadata = details[0].metadata)
        showAtWidth(400.dp, ready.copy(second = ready.second.copy(detail = second)))
        compose.onNodeWithTag("compare-list").performScrollToNode(hasTestTag("compare-differences"))
        compose.onNodeWithTag("compare-differences").performClick().assertIsOn()
        compose.onNodeWithText("已提供的规格相同").assertIsDisplayed()
        compose.onNodeWithText("上下文长度").assertDoesNotExist()
    }

    @Test fun baseQuoteDoesNotAddEmptySectionsEvenWhenServiceIdAndTiersExist() {
        val models = summaries.map { summary ->
            summary.copy(price = summary.price?.copy(cacheRead = null, cacheWrite = null, reasoning = null,
                inputAudio = null, outputAudio = null))
        }
        val state = ready.copy(models = models, first = ready.first.copy(
            detail = details[0].copy(metadata = ModelDetailMetadata(), license = null),
        ), second = ready.second.copy(detail = details[1].copy(metadata = ModelDetailMetadata())))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state) } }

        scrollToDescription("Model A，报价服务商：Default Provider")
        compose.onNodeWithText("USD / 1M tokens").assertIsDisplayed()
        scrollToDescription("Model A，输出：$0.003")
        val index = compose.onNodeWithTag("compare-list").fetchSemanticsNode().config[SemanticsProperties.IndexForKey]
        listOf("more", "basic", "capabilities", "weights", "service-id", "tiers").forEach { assertEquals(-1, index(it)) }
        compose.onNodeWithText("缓存命中输入").assertDoesNotExist()
    }

    @Test fun priceUnitStaysVisibleAndPriceRulesOpenOnDemandAtLargeRtlFont() {
        val quotes = listOf(summaries[0], summaries[1].copy(price = summaries[0].price?.copy(providerName = "Provider B")))
        showAtWidth(320.dp, ready.copy(models = quotes), fontScale = 2f, direction = LayoutDirection.Rtl, darkTheme = true)
        val info = "查看报价说明"
        val rules = "这里显示各模型默认服务的单价。阶梯价和实际计费规则请查阅服务商说明。"
        scrollToText("USD / 1M tokens")
        compose.onNodeWithText(rules).assertDoesNotExist()
        compose.onNodeWithText("报价适用条件以各服务商为准。").assertDoesNotExist()
        scrollToDescription(info)
        val icon = compose.onNodeWithContentDescription(info)
        val heading = compose.onNodeWithText("API 价格").fetchSemanticsNode().boundsInRoot
        assertTrue(icon.fetchSemanticsNode().boundsInRoot.right <= heading.left)
        icon.performClick()
        compose.onNodeWithText("报价说明").assertIsDisplayed()
        compose.onNodeWithText(rules).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("报价说明").assertDoesNotExist()
        icon.assertIsDisplayed()
    }

    @Test fun noQuoteLeavesAnEmptyStateWithoutAPriceInfoButton() {
        showAtWidth(400.dp, ready.copy(models = summaries.map { it.copy(price = null) }))
        scrollToText("暂无基础输入／输出报价")
        compose.onNodeWithContentDescription("查看报价说明").assertDoesNotExist()
        compose.onNodeWithText("USD / 1M tokens").assertDoesNotExist()
    }

    @Test fun canonicalIdsAreNotShownButPickerCanStillSearchAndSelectById() {
        var selection: Pair<Int, String>? = null
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(ready, onSelect = { index, id -> selection = index to id })
        } }
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("关键规格"))
        compose.onNodeWithTag("compare-model-id-toggle").assertDoesNotExist()
        compose.onNodeWithText("查看模型 ID").assertDoesNotExist()
        compose.onNodeWithContentDescription("Model A，模型 ID：deepseek/a").assertDoesNotExist()
        compose.onNode(hasText("deepseek/a") and hasAnyAncestor(hasTestTag("compare-list")), useUnmergedTree = true)
            .assertDoesNotExist()

        compose.onNodeWithTag("compare-list").performScrollToNode(hasText("模型对比"))
        compose.onNodeWithTag("compare-slot-0").performClick()
        val option = hasAnyAncestor(hasTestTag("compare-option:deepseek/a"))
        compose.onNode(hasText("deepseek/a") and option, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("compare-search").performTextInput("deepseek/a")
        compose.onNodeWithTag("compare-option:deepseek/a").assertIsDisplayed()
        compose.onNodeWithTag("compare-option:openai/b").assertDoesNotExist()
        compose.onNode(hasText("deepseek/a") and option, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("compare-option:deepseek/a").performClick()
        compose.runOnIdle { assertEquals(0 to "deepseek/a", selection) }
    }

    @Test fun phoneUsesFullWidthValuePairsAndEqualHeightAlignedSelectionCards() {
        val first = details[0].copy(name = "Kimi K3.1 Pro")
        val second = details[1].copy(name = "DeepSeek V4.1 Flash 1017")
        showAtWidth(400.dp, ready.copy(
            first = ready.first.copy(detail = first), second = ready.second.copy(detail = second)))
        assertSelectionCardsAligned()
        assertTrue(selectionTextLayout("compare-name-1").lineCount > selectionTextLayout("compare-name-0").lineCount)
        compose.onNodeWithTag("compare-logo-0", useUnmergedTree = true).assertWidthIsEqualTo(56.dp)
        assertEquals(2, compose.onAllNodes(hasText("更换"), useUnmergedTree = true).fetchSemanticsNodes().size)
        val selectionCards = hasAnyAncestor(hasTestTag("compare-slot-0") or hasTestTag("compare-slot-1"))
        compose.onNodeWithText("VS", ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("开放权重") and selectionCards, useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(hasText("权重未公开") and selectionCards, useUnmergedTree = true).assertDoesNotExist()
        scrollToDescription("Kimi K3.1 Pro，上下文长度：131,072 tokens")
        val label = compose.onNodeWithText("上下文长度").fetchSemanticsNode().boundsInRoot
        val (firstValue, secondValue) = assertValuePair("Kimi K3.1 Pro，上下文长度：131,072 tokens",
            "DeepSeek V4.1 Flash 1017，上下文长度：400,000 tokens")
        assertTrue(label.bottom <= firstValue.top)
        assertEquals(label.left, firstValue.left, 1f)
        assertEquals(compose.onNodeWithTag("compare-slot-0").fetchSemanticsNode().boundsInRoot.left, firstValue.left, 1f)
        assertEquals(compose.onNodeWithTag("compare-slot-1").fetchSemanticsNode().boundsInRoot.right, secondValue.right, 1f)
        compose.onNode(hasText(first.name) and hasAnyAncestor(hasTestTag("compare-specs")), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test fun phoneReusesAlignedValuePairsAcrossGroupsAndCentersShortTextBesideLongLicense() {
        val license = "A complete license name that must not be truncated even when the text wraps across several lines"
        val state = ready.copy(first = ready.first.copy(detail = details[0].copy(license = license)),
            second = ready.second.copy(detail = details[1].copy(license = "MIT")))
        showAtWidth(400.dp, state, darkTheme = true)
        val rows = listOf(
            "Model A，输出：$0.003" to "Model B，输出：暂无数据",
            "Model A，发布时间：2026-10" to "Model B，发布时间：暂无数据",
            "Model A，工具调用：不支持" to "Model B，工具调用：暂无数据",
            "Model A，模型许可：$license" to "Model B，模型许可：MIT",
        )
        val pairs = rows.map { (first, second) ->
            scrollToDescription(first)
            assertValuePair(first, second)
        }
        val (longCell, shortCell) = pairs.last()
        assertTrue(longCell.height > pairs.first().first.height)
        pairs.forEach { (first, second) ->
            assertEquals(longCell.left, first.left, 1f)
            assertEquals(shortCell.right, second.right, 1f)
        }
        val shortText = compose.onNodeWithText("MIT", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(shortCell.center.x, shortText.center.x, 1f)
        assertEquals(shortCell.center.y, shortText.center.y, 1f)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(license, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(layout.lineCount > 1)
        assertFalse(layout.hasVisualOverflow)
        assertEquals(license.length, layout.getLineEnd(layout.lineCount - 1))
        saveRegressionScreenshot("compare-long-license-dark")
    }

    @Test fun selectionCardsCollapseTogetherDuringSwipesAndReturnBelowTitle() {
        showAtWidth(400.dp, maxHeight = 520.dp)
        val list = compose.onNodeWithTag("compare-list")
        val title = compose.onNodeWithText("模型对比")
        val first = compose.onNodeWithTag("compare-slot-0")
        val second = compose.onNodeWithTag("compare-slot-1")
        val initial = first.fetchSemanticsNode().boundsInRoot
        val logo = compose.onNodeWithTag("compare-logo-0", useUnmergedTree = true)
        val name = compose.onNodeWithTag("compare-name-0", useUnmergedTree = true)
        logo.assertWidthIsEqualTo(56.dp)
        assertTrue(initial.top > title.fetchSemanticsNode().boundsInRoot.bottom)
        assertEquals(logo.fetchSemanticsNode().boundsInRoot.center.x, name.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        saveRegressionScreenshot("compare-selection-expanded")
        val initialGap = initial.top - list.fetchSemanticsNode().boundsInRoot.top
        fun scrollBy(pixels: Float) = list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
        scrollBy(initialGap * 0.65f)
        val midway = first.fetchSemanticsNode().boundsInRoot
        assertTrue(midway.height < initial.height)
        assertEquals(midway.height, second.fetchSemanticsNode().boundsInRoot.height, 1f)
        saveRegressionScreenshot("compare-selection-collapsing")
        scrollBy(-initialGap * 0.2f)
        assertTrue(first.fetchSemanticsNode().boundsInRoot.height > midway.height)

        fun swipeList(forward: Boolean) = list.performTouchInput {
            val upper = Offset(center.x, height * 0.32f)
            val lower = Offset(center.x, height * 0.82f)
            swipe(if (forward) lower else upper, if (forward) upper else lower, 500)
        }
        swipeList(forward = true)
        title.assertIsNotDisplayed()
        val pinned = first.fetchSemanticsNode().boundsInRoot
        assertEquals(list.fetchSemanticsNode().boundsInRoot.top, pinned.top, 1f)
        assertTrue(pinned.height < midway.height)
        assertEquals(pinned.top, second.fetchSemanticsNode().boundsInRoot.top, 1f)
        logo.assertWidthIsEqualTo(36.dp)
        val arrow = compose.onNodeWithTag("compare-expand-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(logo.fetchSemanticsNode().boundsInRoot.right <= name.fetchSemanticsNode().boundsInRoot.left)
        assertTrue(name.fetchSemanticsNode().boundsInRoot.right <= arrow.left)
        compose.onNodeWithTag("compare-lab-0", useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithText("VS", ignoreCase = true, useUnmergedTree = true).assertDoesNotExist()
        // LazyColumn 的语义滚动位置是估算值，跨过较高的分区时不一定单调。
        val prices = compose.onNodeWithText("API 价格").assertIsDisplayed()
        val beforeFurtherScroll = prices.fetchSemanticsNode().positionInRoot.y
        swipeList(forward = true)
        prices.assertIsDisplayed()
        assertTrue(prices.fetchSemanticsNode().positionInRoot.y < beforeFurtherScroll)
        first.assertIsDisplayed()
        second.assertIsDisplayed()
        assertEquals(pinned, first.fetchSemanticsNode().boundsInRoot)
        saveRegressionScreenshot("compare-selection-pinned")

        repeat(4) { swipeList(forward = false) }
        title.assertIsDisplayed()
        assertEquals(initial, first.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun pinnedSelectionCardsStillHandleReplaceRemoveAndReselectWithoutJumpingBack() {
        var removed = -1
        var selected: Pair<Int, String>? = null
        val replacement = ModelSummary("other/c", "Model C")
        val state = mutableStateOf(ready.copy(models = summaries + replacement))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onSelect = { slot, id ->
                selected = slot to id
                state.value = if (slot == 0) {
                    state.value.copy(first = CompareModelState(id, CompareReadStatus.CONTENT,
                        details[0].copy(id = id, name = replacement.name)))
                } else {
                    state.value.copy(second = ready.second)
                }
            }, onRemove = { slot ->
                removed = slot
                state.value = state.value.copy(second = CompareModelState())
            })
        } }
        val list = compose.onNodeWithTag("compare-list")
        scrollToText("权重与许可")
        val first = compose.onNodeWithTag("compare-slot-0")
        assertEquals(list.fetchSemanticsNode().boundsInRoot.top, first.fetchSemanticsNode().boundsInRoot.top, 1f)
        first.performTouchInput { click() }
        compose.onNodeWithTag("compare-option:openai/b").assertIsNotEnabled()
        compose.onNodeWithTag("compare-option:other/c").performClick()
        compose.runOnIdle { assertEquals(0 to "other/c", selected) }
        compose.onNodeWithTag("compare-name-0", useUnmergedTree = true).assertTextEquals("Model C")
        compose.onNodeWithTag("compare-slot-1").performTouchInput { click() }
        compose.onNodeWithContentDescription("移除对比模型：Model B").performClick()
        compose.runOnIdle { assertEquals(1, removed) }
        compose.onNodeWithText("选择模型 2").assertIsDisplayed()
        compose.onNodeWithText("权重与许可").assertDoesNotExist()
        val expanded = first.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("compare-slot-1").performClick()
        compose.onNodeWithTag("compare-option:openai/b").performClick()
        compose.onNodeWithText("模型对比").assertIsDisplayed()
        compose.onNodeWithText("关键规格").assertIsDisplayed()
        assertEquals(expanded, first.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun selectionCardsScrollAwayInShortWindowsToLeaveRoomForContent() {
        showAtWidth(400.dp, maxHeight = 230.dp)
        compose.onNodeWithTag("compare-slot-0").assertIsDisplayed()
        scrollToText("权重与许可")
        compose.onNodeWithTag("compare-slot-0").assertIsNotDisplayed()
        compose.onNodeWithTag("compare-slot-1").assertIsNotDisplayed()
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

        scrollToText("权重与许可")
        val logo = compose.onNodeWithTag("compare-logo-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val name = compose.onNodeWithTag("compare-name-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val arrow = compose.onNodeWithTag("compare-expand-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(logo.left >= name.right)
        assertTrue(name.left >= arrow.right)
        selectionTextLayout("compare-name-1")
    }

    @Test fun wideLayoutKeepsAlignedFieldAndValueColumns() {
        showAtWidth(840.dp, maxHeight = 400.dp, dynamicColor = true)
        scrollToDescription("Model A，上下文长度：131,072 tokens")
        val label = compose.onNodeWithText("上下文长度").fetchSemanticsNode().boundsInRoot
        val firstValue = compose.onNodeWithContentDescription("Model A，上下文长度：131,072 tokens").fetchSemanticsNode().boundsInRoot
        val secondValue = compose.onNodeWithContentDescription("Model B，上下文长度：400,000 tokens").fetchSemanticsNode().boundsInRoot
        assertEquals(label.center.y, firstValue.center.y, 1f)
        assertEquals(firstValue.top, secondValue.top, 1f)
        assertTrue(label.right < firstValue.left)
        assertTrue(firstValue.right < secondValue.left)
        scrollToDescription("Model A，报价服务商：Default Provider")
        assertEquals(firstValue.left,
            compose.onNodeWithContentDescription("Model A，报价服务商：Default Provider").fetchSemanticsNode().boundsInRoot.left, 1f)
    }

    @Test fun modelInfoIsVisibleWithoutExpandingAndUpdatesAfterReplacement() {
        val state = mutableStateOf(ready)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        scrollToText("基本信息")
        scrollToDescription("Model A，工具调用：不支持")
        scrollToDescription("Model A，推理：支持")
        scrollToDescription("Model B，推理：暂无数据")
        val oldLicense = "Model A，模型许可：A complete license name that must not be truncated"
        scrollToDescription(oldLicense)
        compose.onNodeWithText("查看完整许可").assertDoesNotExist()
        compose.runOnIdle {
            val replacement = details[0].copy(id = "deepseek/replacement", name = "Replacement", license = "MIT")
            state.value = ready.copy(models = summaries + ModelSummary(replacement.id, replacement.name),
                first = CompareModelState(replacement.id, CompareReadStatus.CONTENT, replacement))
        }
        scrollToDescription("Replacement，模型许可：MIT")
        compose.onNodeWithContentDescription(oldLicense).assertDoesNotExist()
    }

    @Test fun selectedSlotsWithoutNamesUseAccessiblePlaceholdersInsteadOfIds() {
        var removed = -1
        var retried = -1
        val state = mutableStateOf(ModelCompareUiState(catalogStatus = CompareReadStatus.CONTENT,
            first = CompareModelState("missing/first", CompareReadStatus.NOT_FOUND),
            second = CompareModelState("missing/second", CompareReadStatus.ERROR)))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onRemove = { removed = it }, onRetry = { retried = it })
        } }
        compose.onNodeWithTag("compare-name-0", useUnmergedTree = true).assertTextEquals("模型 1（名称暂无数据）")
        compose.onNodeWithTag("compare-name-1", useUnmergedTree = true).assertTextEquals("模型 2（名称暂无数据）")
        listOf("missing/first", "missing/second").forEach { id ->
            compose.onNodeWithText(id, useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithContentDescription("移除对比模型：模型 1（名称暂无数据）").performClick()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle {
            assertEquals(0, removed)
            assertEquals(1, retried)
            state.value = state.value.copy(first = CompareModelState("missing/first"))
        }
        compose.onNodeWithText("正在读取本地模型数据…").assertIsDisplayed()
        compose.onNodeWithTag("compare-name-0", useUnmergedTree = true).assertTextEquals("模型 1（名称暂无数据）")
        compose.onNodeWithText("missing/first", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("compare-slot-0").performClick()
        compose.onNodeWithText("本地暂无可选模型，请先到“模型”页加载目录。").assertIsDisplayed()
    }

    @Test fun loadingMissingAndErrorNeverShowOldComparisonAndRetryIsLocalCallback() {
        var retries = 0
        val state = mutableStateOf(ready.copy(first = CompareModelState("deepseek/a", CompareReadStatus.NOT_FOUND)))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) {
            ModelCompareScreen(state.value, onRetry = { retries++ })
        } }
        compose.onNodeWithText("此模型不在当前目录").assertIsDisplayed()
        compose.onNodeWithText("评测分数").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(first = CompareModelState("deepseek/a", CompareReadStatus.ERROR)) }
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
        compose.runOnIdle { state.value = state.value.copy(first = CompareModelState("deepseek/a")) }
        compose.onNodeWithText("正在读取本地模型数据…").assertIsDisplayed()
        compose.onNodeWithText("评测分数").assertDoesNotExist()
    }

    @Test fun readingPositionSurvivesAsyncRestorationWithAllSectionsVisible() {
        val restoration = StateRestorationTester(compose)
        val state = mutableStateOf(ready)
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        scrollToText("权重与许可")
        val before = compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(before, compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { state.value = ready.copy(first = CompareModelState("deepseek/a"), second = CompareModelState("openai/b")) }
        // 详情先到、目录／默认报价稍后到，也不能提前放置缺少报价行的正文。
        compose.runOnIdle { state.value = ready.copy(models = emptyList(), catalogStatus = CompareReadStatus.LOADING) }
        compose.onNodeWithText("正在读取目录和报价…").assertIsDisplayed()
        compose.onNodeWithText("正在读取本地模型数据…").assertDoesNotExist()
        compose.onNodeWithText("评测分数").assertDoesNotExist()
        compose.runOnIdle { state.value = ready }
        assertEquals(before, compose.onNodeWithText("权重与许可").fetchSemanticsNode().boundsInRoot.top, 1f)
        scrollToDescription("Model A，缓存命中输入：$0.001")
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
        // 滚动可能隐藏导航，反向轻滑唤回后再记录需要恢复的阅读位置。
        compose.onNodeWithTag("compare-list").performTouchInput { swipe(center, center + Offset(0f, 100f), 400) }
        compose.onNodeWithTag("glass-navigation").assertIsDisplayed()
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
        maxHeight: Dp = Dp.Infinity, dynamicColor: Boolean = false,
    ) {
        compose.setContent {
            BoxWithConstraints {
                val density = LocalDensity.current
                val scale = minOf(1f, maxWidth / width)
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, fontScale),
                    LocalLayoutDirection provides direction) {
                    ModelAtlasTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                        Box(Modifier.width(width).heightIn(max = maxHeight)) { ModelCompareScreen(state) }
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
        listOf("name", "lab").forEach { field ->
            val left = compose.onNodeWithTag("compare-$field-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val right = compose.onNodeWithTag("compare-$field-1", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals("$field top", left.top, right.top, 1f)
            assertEquals("$field bottom", left.bottom, right.bottom, 1f)
            selectionTextLayout("compare-$field-0")
            selectionTextLayout("compare-$field-1")
        }
    }

    private fun assertValuePair(
        firstDescription: String, secondDescription: String, direction: LayoutDirection = LayoutDirection.Ltr,
    ): Pair<Rect, Rect> {
        val first = compose.onNodeWithContentDescription(firstDescription).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithContentDescription(secondDescription).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertEquals(first.height, second.height, 1f)
        assertEquals(first.width, second.width, 1f)
        assertTrue(if (direction == LayoutDirection.Ltr) first.right < second.left else second.right < first.left)
        return first to second
    }

    private fun selectionTextLayout(tag: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertFalse(layout.didOverflowHeight)
        repeat(layout.lineCount) { line ->
            assertFalse(layout.isLineEllipsized(line))
            // Android 的行边界包含换行处的尾随空格；只检查可见字符是否越界。
            for (offset in layout.getLineStart(line) until layout.getLineEnd(line, visibleEnd = true)) {
                val glyph = layout.getBoundingBox(offset)
                assertTrue("$tag line $line offset $offset: $glyph, width=${layout.size.width}",
                    glyph.left >= -1f && glyph.right <= layout.size.width + 1f)
            }
        }
        assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
        return layout
    }

    private fun scrollToDescription(description: String) {
        compose.onNodeWithTag("compare-list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    private fun scrollToText(text: String) {
        compose.onNodeWithTag("compare-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

}
