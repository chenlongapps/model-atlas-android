package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelCatalogControlsTest {
    @get:Rule val compose = createComposeRule()
    private val models = listOf(
        ModelSummary(
            "alpha/vision", "Atlas Vision", openWeights = true,
            inputModalities = listOf("text", "image", "custom"), outputModalities = listOf("text"),
            contextLimitTokens = 128_000, outputLimitTokens = 32_000,
        ),
        ModelSummary(
            "beta/chat", "Atlas Chat", openWeights = false,
            inputModalities = listOf("text"), outputModalities = listOf("text", "image"),
        ),
        ModelSummary("unknown/model", "Unknown Model"),
    )

    @Test fun searchUpdatesCountAndClearSearchKeepsFilters() {
        val state = mutableStateOf(ModelListUiState(models, isLoading = false, query = ModelListQuery(weights = WeightFilter.OPEN)))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithTag("model-search").performTextReplacement("beta")
        compose.onNodeWithTag("model-search").performImeAction()
        compose.onNodeWithText("匹配 0 / 3 个模型").assertIsDisplayed()
        compose.onNodeWithText("没有匹配的模型").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("catalog-query"))
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithText("筛选 · 1").assertIsDisplayed()
        compose.onNodeWithText("匹配 1 / 3 个模型").assertIsDisplayed()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:alpha/vision"))
        compose.onNodeWithText("Atlas Vision").assertIsDisplayed()
        compose.onNodeWithText("Atlas Chat").assertDoesNotExist()
        compose.runOnIdle { assertEquals(WeightFilter.OPEN, state.value.query.weights) }
    }

    @Test fun noResultsResetClearsSearchAndFiltersButEmptyCatalogKeepsOwnState() {
        val state = mutableStateOf(ModelListUiState(models, isLoading = false, query = ModelListQuery(search = "missing", labs = setOf("alpha"))))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithText("目录暂时为空").assertDoesNotExist()
        compose.onNodeWithText("清除全部条件").performScrollTo().performClick()
        compose.onNodeWithText("模型目录 · 3 个模型").assertIsDisplayed()
        compose.onNodeWithText("筛选 · 0").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(models = emptyList()) }
        compose.onNodeWithText("目录暂时为空").assertIsDisplayed()
        compose.onNodeWithText("没有匹配的模型").assertDoesNotExist()
    }

    @Test fun sheetCombinesDirectionalFiltersAndResetKeepsSearch() {
        val state = mutableStateOf(ModelListUiState(models, isLoading = false, query = ModelListQuery(search = "atlas")))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithText("筛选 · 0").performClick()
        compose.onNodeWithTag("filter-lab:alpha").performScrollTo().performClick()
        compose.onNodeWithTag("filter-lab:beta").performScrollTo().performClick()
        compose.onNodeWithTag("filter-result-count").assertTextContains("预览匹配 2 / 3 个模型")
        compose.onNodeWithTag("filter-input:image").performScrollTo().performClick()
        compose.onNodeWithTag("filter-result-count").assertTextContains("预览匹配 1 / 3 个模型")
        compose.onNodeWithTag("filter-output:image").performScrollTo().performClick()
        compose.onNodeWithTag("filter-result-count").assertTextContains("预览匹配 0 / 3 个模型")
        compose.onNodeWithTag("filter-input:custom").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(ModelListQuery(search = "atlas"), state.value.query) }
        compose.onNodeWithTag("filter-reset").performClick()
        compose.onNodeWithTag("filter-result-count").assertTextContains("预览匹配 2 / 3 个模型")
        compose.onNodeWithTag("filter-apply").performClick()
        compose.onNodeWithTag("model-search").assertTextContains("atlas")
        compose.runOnIdle { assertEquals(ModelListQuery(search = "atlas"), state.value.query) }
    }

    @Test fun narrowDarkLargeFontSheetKeepsFooterAndSelectedOptionsAccessibleAfterRefresh() {
        val state = mutableStateOf(ModelListUiState(models, isLoading = false))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ModelAtlasTheme(darkTheme = true, dynamicColor = false) {
                    ModelListScreen(
                        state.value, modifier = Modifier.width(320.dp), windowInsets = WindowInsets(0),
                        onQueryChange = { state.value = state.value.copy(query = it) },
                    )
                }
            }
        }
        compose.onNodeWithText("筛选 · 0").performClick()
        compose.onNodeWithTag("filter-lab:alpha").performScrollTo().performClick()
        // 目录变化不会自动丢弃正在使用的条件，消失的选项仍可取消。
        compose.runOnIdle { state.value = state.value.copy(models = models.drop(1)) }
        compose.onNodeWithTag("filter-lab:alpha").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("filter-context:128000").performScrollTo().performClick()
        compose.onNodeWithTag("filter-output-limit:32000").performScrollTo().performClick()
        compose.onNodeWithTag("filter-price:false").performScrollTo().performClick()
        compose.onNodeWithTag("filter-reset").assertIsDisplayed().performClick()
        compose.onNodeWithTag("filter-apply").assertIsDisplayed().performClick()
        compose.onNodeWithText("筛选 · 0").assertIsDisplayed()
    }

    @Test fun tabSwitchAndUiRestorationKeepQueryAndSheetSelection() {
        val state = mutableStateOf(ModelListUiState(models, isLoading = false))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold { padding, listState, _ ->
                    ModelListScreen(
                        state.value, bottomOverlayPadding = padding, listState = listState,
                        onQueryChange = { state.value = state.value.copy(query = it) },
                    )
                }
            }
        }
        compose.onNodeWithTag("model-search").performTextReplacement("atlas")
        compose.onNodeWithTag("model-search").performImeAction()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performClick()
        compose.onNodeWithTag("model-search").assertTextContains("atlas")
        compose.onNodeWithText("筛选 · 0").performClick()
        compose.onNodeWithTag("filter-weights:UNKNOWN").performScrollTo().performClick().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("filter-weights:UNKNOWN").performScrollTo().assertIsSelected()
        compose.runOnIdle { assertEquals(WeightFilter.ALL, state.value.query.weights) }
        compose.onNodeWithTag("filter-apply").performClick()
        compose.onNodeWithText("匹配 0 / 3 个模型").assertIsDisplayed()
    }

    @Test fun systemBackDiscardsDraftAndOnlyApplyCommits() {
        val initial = ModelListQuery(search = "atlas", weights = WeightFilter.OPEN)
        val state = mutableStateOf(ModelListUiState(models, isLoading = false, query = initial))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithText("筛选 · 1").performClick()
        compose.onNodeWithTag("filter-weights:CLOSED").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial, state.value.query) }
        compose.onNodeWithContentDescription("关闭筛选，放弃未应用的修改").assertDoesNotExist()
        pressBack()
        compose.onNodeWithText("筛选 · 1").performClick()
        compose.onNodeWithTag("filter-weights:OPEN").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("filter-reset").performClick()
        pressBack()
        compose.runOnIdle { assertEquals(initial, state.value.query) }
        compose.onNodeWithText("筛选 · 1").performClick()
        compose.onNodeWithTag("filter-weights:OPEN").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("filter-weights:CLOSED").performScrollTo().performClick()
        compose.onNodeWithTag("filter-apply").assertTextContains("应用").performClick()
        compose.runOnIdle { assertEquals(initial.copy(weights = WeightFilter.CLOSED), state.value.query) }
        compose.onNodeWithTag("active-filter:weights").assertIsDisplayed()
    }

    @Test fun removableConditionsAndResetNeverClearSearchOrUnrelatedConditions() {
        val initial = ModelListQuery(
            search = "atlas", labs = setOf("alpha"), weights = WeightFilter.OPEN,
            inputModalities = setOf("image"), minContextTokens = 128_000, hasPrice = false,
        )
        val state = mutableStateOf(ModelListUiState(models, isLoading = false, query = initial))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithTag("active-filter:context").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.copy(minContextTokens = null), state.value.query) }
        compose.onNodeWithTag("active-filter:lab:alpha").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(initial.copy(minContextTokens = null, labs = emptySet()), state.value.query) }
        compose.onNodeWithTag("reset-filters").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(ModelListQuery(search = "atlas"), state.value.query) }
        compose.onNodeWithTag("model-search").assertTextContains("atlas")
        compose.onNodeWithTag("active-filter:weights").assertDoesNotExist()
    }

    @Test fun labBrandNamesKeepRawIdsInSelectionsAndRemovableTags() {
        val catalog = listOf(ModelSummary("openai/one", "One"), ModelSummary("deepseek/two", "Two"))
        val state = mutableStateOf(ModelListUiState(catalog, isLoading = false))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithText("筛选 · 0").performClick()
        compose.onNodeWithText("研发机构").assertIsDisplayed()
        compose.onNodeWithTag("filter-lab:openai").performScrollTo().assertTextContains("OpenAI").performClick()
        compose.onNodeWithTag("filter-lab:deepseek").assertTextContains("DeepSeek")
        compose.onNodeWithTag("filter-result-count").assertTextContains("预览匹配 1 / 2 个模型")
        compose.onNodeWithTag("filter-apply").performClick()
        compose.runOnIdle { assertEquals(setOf("openai"), state.value.query.labs) }
        compose.onNodeWithTag("active-filter:lab:openai").assertTextContains("研发机构：OpenAI")
        compose.onNodeWithContentDescription("移除筛选：研发机构：OpenAI").performClick()
        compose.runOnIdle { assertEquals(emptySet<String>(), state.value.query.labs) }
    }

    @Test fun collapsedLabsPreferWellKnownLabsOverAlphabeticalOrder() {
        val popularLabs = listOf("openai", "anthropic", "google", "deepseek", "alibaba", "meta", "xai", "moonshotai")
        val catalog = (popularLabs + listOf("ai21", "aaa", "mistral")).sorted().map {
            ModelSummary("$it/model", it)
        }
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(ModelListUiState(catalog, isLoading = false))
            }
        }
        compose.onNodeWithText("筛选 · 0").performClick()
        popularLabs.forEach { compose.onNodeWithTag("filter-lab:$it").assertExists() }
        compose.onNodeWithTag("filter-lab:ai21").assertDoesNotExist()
        compose.onNodeWithTag("filter-lab:aaa").assertDoesNotExist()
        compose.onNodeWithTag("filter-lab:mistral").assertDoesNotExist()
        compose.onNodeWithTag("filter-labs-more").performScrollTo().performClick()
        compose.onNodeWithTag("filter-lab:ai21").assertExists()
        compose.onNodeWithTag("filter-lab:aaa").assertExists()
        compose.onNodeWithTag("filter-lab:mistral").assertExists()
    }

    @Test fun otherLabsUseFullCatalogCountsEvenWhenSearchMatchesNothing() {
        val counts = linkedMapOf("zzz" to 9, "yyy" to 8, "xxx" to 7, "www" to 6, "vvv" to 5, "uuu" to 4, "ttt" to 3, "aaa" to 2)
        val catalog = listOf(ModelSummary("openai/one", "One")) + counts.flatMap { (lab, count) ->
            List(count) { ModelSummary("$lab/$it", "$lab $it") }
        }
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelFilterSheet(
                    models = catalog, query = ModelListQuery(search = "no-match"),
                    onQueryChange = {}, onDismiss = {},
                )
            }
        }
        compose.onNodeWithTag("filter-result-count").assertTextContains("预览匹配 0 / ${catalog.size} 个模型")
        compose.onNodeWithTag("filter-lab:openai").assertExists()
        counts.keys.take(7).forEach { compose.onNodeWithTag("filter-lab:$it").assertExists() }
        compose.onNodeWithTag("filter-lab:aaa").assertDoesNotExist()
        compose.onNodeWithTag("filter-labs-more").performScrollTo().performClick()
        compose.onNodeWithTag("filter-lab:aaa").assertExists()
    }

    @Test fun moreLabsCanExpandAndCollapseWithoutHidingSelectedLab() {
        val catalog = List(12) { ModelSummary("lab${it.toString().padStart(2, '0')}/model", "模型 $it") }
        val state = mutableStateOf(ModelListUiState(catalog, isLoading = false))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelListScreen(state.value, onQueryChange = { state.value = state.value.copy(query = it) })
            }
        }
        compose.onNodeWithText("筛选 · 0").performClick()
        compose.onNodeWithTag("filter-lab:lab11").assertDoesNotExist()
        compose.onNodeWithTag("filter-labs-more").performScrollTo().performClick()
        compose.onNodeWithTag("filter-lab:lab11").performScrollTo().performClick()
        compose.onNodeWithTag("filter-labs-more").performScrollTo().performClick()
        compose.onNodeWithTag("filter-lab:lab11").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("filter-apply").performClick()
        compose.runOnIdle { assertEquals(setOf("lab11"), state.value.query.labs) }
        compose.onNodeWithTag("active-filter:lab:lab11").assertIsDisplayed()
    }
}
