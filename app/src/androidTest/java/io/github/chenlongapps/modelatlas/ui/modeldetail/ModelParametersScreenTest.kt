package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.testing.realAtlasParameterDetail
import io.github.chenlongapps.modelatlas.ui.modelcompare.CompareModelState
import io.github.chenlongapps.modelatlas.ui.modelcompare.CompareReadStatus
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareScreen
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModelParametersScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun real(id: String) = realAtlasParameterDetail(id)
    private fun content(model: ModelDetail) = ModelDetailUiState(model.id, ModelDetailStatus.CONTENT, model)

    @Test fun publishedMoeDenseAndUndisclosedCountsRemainDistinct() {
        val moe = real("deepseek/deepseek-r1")
        val dense = real("alibaba/qwen3-8b")
        val unknown = real("openai/gpt-4o")
        val model = mutableStateOf(moe)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model.value), {}) } }
        detailText("总参数量：671 B\n激活量：37 B")
        compose.runOnIdle { model.value = dense }
        detailText("总参数量：8.2 B\n激活量：暂无数据")
        compose.runOnIdle { model.value = unknown }
        detailText("总参数量：暂无数据\n激活量：暂无数据")
    }

    @Test fun activeOnlyAndComponentsOnlyShowJustCanonicalCounts() {
        val active = real("deepseek/deepseek-v4.1-flash")
        val components = real("mistral/pixtral-12b")
        val model = mutableStateOf(active)
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(content(model.value), {}) } }
        detailText("总参数量：暂无数据\n激活量：16 B")
        assertParameterExtrasAbsent(active)
        compose.runOnIdle { model.value = components }
        detailText("总参数量：暂无数据\n激活量：暂无数据")
        assertParameterExtrasAbsent(components)
        compose.onNodeWithText("总参数量：12.4 B").assertDoesNotExist()
    }

    @Test fun detailCountsSurviveTabAndAsyncRestorationAndUpdateForDifferentId() {
        val original = real("deepseek/deepseek-r1")
        val state = mutableStateOf(content(original))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelDetailScreen(state.value, {}) } }
        detailText("总参数量：671 B\n激活量：37 B")
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = ModelDetailUiState(original.id) }
        compose.runOnIdle { state.value = content(original) }
        detailText("总参数量：671 B\n激活量：37 B")
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(1)
        compose.onNodeWithTag("detail-tab:PROVIDERS").performClick()
        compose.onNodeWithTag("detail-tab:OVERVIEW").performClick()
        detailText("总参数量：671 B\n激活量：37 B")
        compose.runOnIdle { state.value = content(real("alibaba/qwen3-8b")) }
        detailText("总参数量：8.2 B\n激活量：暂无数据")
    }

    @Test fun darkNarrowRtlKeepsExactCountsWithoutClipping() {
        val count = real("abacusai/dracarys-llama-3.1-70b-instruct")
        val active = real("deepseek/deepseek-v4.1-flash")
        val model = mutableStateOf(count)
        compose.setContent { NarrowDarkRtl { ModelDetailScreen(content(model.value), {}) } }
        detailText("总参数量：70.553706496 B\n激活量：暂无数据")
        assertTextNotClipped("总参数量：70.553706496 B\n激活量：暂无数据")
        compose.runOnIdle { model.value = active }
        detailText("总参数量：暂无数据\n激活量：16 B")
        assertTextNotClipped("总参数量：暂无数据\n激活量：16 B")
    }

    @Test fun comparisonShowsOnlyCanonicalCountsWithoutFillingDenseActiveOrComponentTotal() {
        val moe = real("deepseek/deepseek-r1")
        val active = real("deepseek/deepseek-v4.1-flash")
        val state = mutableStateOf(ready(moe, active))
        compose.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        compareDescription("${moe.name}，总参数量：671 B")
        compareDescription("${moe.name}，激活量：37 B")
        compareDescription("${active.name}，总参数量：暂无数据")
        compareDescription("${active.name}，激活量：16 B")
        assertParameterExtrasAbsent(active)
        val dense = real("alibaba/qwen3-8b")
        val component = real("mistral/pixtral-12b")
        compose.runOnIdle { state.value = ready(dense, component) }
        compareDescription("${dense.name}，总参数量：8.2 B")
        compareDescription("${dense.name}，激活量：暂无数据")
        compareDescription("${component.name}，总参数量：暂无数据")
        compareDescription("${component.name}，激活量：暂无数据")
        assertParameterExtrasAbsent(component)
    }

    @Test fun comparisonCountsRestoreAndUpdateOnReplacement() {
        val first = real("mistral/pixtral-12b")
        val second = real("deepseek/deepseek-r1")
        val initial = ready(first, second)
        val state = mutableStateOf(initial)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { ModelAtlasTheme(dynamicColor = false) { ModelCompareScreen(state.value) } }
        compareDescription("${first.name}，总参数量：暂无数据")
        compareDescription("${second.name}，激活量：37 B")
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = initial.copy(first = CompareModelState(first.id)) }
        compose.runOnIdle { state.value = initial }
        compareDescription("${first.name}，总参数量：暂无数据")
        val replacement = real("alibaba/qwen3-8b")
        compose.runOnIdle { state.value = ready(replacement, second) }
        compareDescription("${replacement.name}，总参数量：8.2 B")
        compareDescription("${replacement.name}，激活量：暂无数据")
    }

    @Test fun narrowDarkRtlComparisonKeepsFullCountsReadable() {
        val first = real("abacusai/dracarys-llama-3.1-70b-instruct")
        val second = real("deepseek/deepseek-v4.1-flash")
        compose.setContent { NarrowDarkRtl { ModelCompareScreen(ready(first, second)) } }
        compareDescription("${first.name}，总参数量：70.553706496 B")
        assertTextNotClipped("70.553706496 B")
        compareDescription("${second.name}，激活量：16 B")
        assertTextNotClipped("16 B")
    }

    @Composable private fun NarrowDarkRtl(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f),
            LocalLayoutDirection provides LayoutDirection.Rtl) {
            ModelAtlasTheme(darkTheme = true, dynamicColor = false) { Box(Modifier.width(320.dp)) { content() } }
        }
    }

    private fun ready(first: ModelDetail, second: ModelDetail) = ModelCompareUiState(
        models = listOf(first, second).map { ModelSummary(it.id, it.name) }, catalogStatus = CompareReadStatus.CONTENT,
        first = CompareModelState(first.id, CompareReadStatus.CONTENT, first),
        second = CompareModelState(second.id, CompareReadStatus.CONTENT, second),
    )

    private fun assertParameterExtrasAbsent(model: ModelDetail) {
        compose.onNodeWithTag("overview-parameters-toggle").assertDoesNotExist()
        compose.onNodeWithTag("compare-parameters-0-toggle").assertDoesNotExist()
        compose.onNodeWithTag("compare-parameters-1-toggle").assertDoesNotExist()
        compose.onNodeWithText("统计口径").assertDoesNotExist()
        compose.onNodeWithText("打开：参数来源（外部链接）").assertDoesNotExist()
        model.parameters?.notes?.let { compose.onNodeWithText(it, substring = true).assertDoesNotExist() }
        model.parameters?.components.orEmpty().forEach {
            compose.onNodeWithText(it.name).assertDoesNotExist()
        }
    }

    private fun detailText(text: String) {
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(2)
        compose.onNodeWithTag("model-detail-content").performScrollToNode(hasText(text))
        compose.onNodeWithText(text, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    private fun compareDescription(description: String) {
        compose.onNodeWithTag("compare-list").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
    }

    private fun assertTextNotClipped(text: String) {
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
}
