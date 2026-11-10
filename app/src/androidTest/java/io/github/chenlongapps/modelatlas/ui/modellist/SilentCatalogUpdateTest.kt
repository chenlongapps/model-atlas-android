package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailScreen
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailStatus
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SilentCatalogUpdateTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var repository: Repository
    private lateinit var viewModel: ModelListViewModel

    @After fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    }

    @Test fun backgroundReplacementKeepsVisibleCardPositionAndDetailReturnSource() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasTestTag("model:lab/20"))
        val before = compose.onNodeWithTag("model:lab/20").getUnclippedBoundsInRoot().top
        finishBackground()
        compose.onNodeWithText("Cached 20").assertIsDisplayed()
        assertEquals(before.value, compose.onNodeWithTag("model:lab/20").getUnclippedBoundsInRoot().top.value, 1f)
        compose.onNodeWithTag("catalog-notice").assertDoesNotExist()
        compose.onNodeWithText("Cached 20").performClick()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Cached 20").assertIsDisplayed()
        assertEquals(before.value, compose.onNodeWithTag("model:lab/20").getUnclippedBoundsInRoot().top.value, 1f)
        compose.runOnIdle { assertEquals(1, repository.requests) }
    }

    @Test fun tabSwitchAndRecompositionKeepFrozenCountQueryAndExpandedPrices() {
        show()
        compose.runOnIdle { viewModel.setQuery(ModelListQuery(search = "Cached")) }
        compose.onNodeWithContentDescription("展开档位价格").performClick()
        finishBackground()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("nav-models").performClick()
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
        compose.onNodeWithText("Cached 0").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(40, viewModel.uiState.value.models.size)
            assertEquals("Cached", viewModel.uiState.value.query.search)
            assertEquals(1, repository.requests)
        }
        compose.onNodeWithText("立即更新").assertDoesNotExist()
    }

    @Test fun explicitRefreshFetchesAgainPublishesCachedUpdateAndReportsFailures() {
        show()
        finishBackground()
        compose.onNodeWithText("Cached 0").assertIsDisplayed()
        refreshThroughAccessibility()
        compose.onNodeWithText("Latest").assertIsDisplayed()
        compose.onNodeWithText("模型目录已更新").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, repository.requests) }
        refreshThroughAccessibility()
        compose.onNodeWithText("已是最新数据").assertIsDisplayed()
        compose.runOnIdle { repository.failure = IOException("offline") }
        refreshThroughAccessibility()
        compose.onNodeWithText("Latest").assertIsDisplayed()
        compose.onNodeWithText("无法连接模型目录，请检查网络后重试。").assertIsDisplayed()
        compose.runOnIdle { assertEquals(4, repository.requests) }
    }

    private fun refreshThroughAccessibility() {
        val actions = compose.onNodeWithTag("model-list").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { check(actions.single { it.label == "刷新目录" }.action()) }
    }

    private fun finishBackground() {
        compose.runOnIdle { repository.gate.complete(Unit) }
        compose.waitUntil { repository.models.value == repository.remote }
    }

    private fun show() {
        repository = Repository()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewModel = ModelListViewModel(repository, SavedStateHandle())
            store.put("list", viewModel)
        }
        compose.setContent {
            val state by viewModel.uiState.collectAsState()
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(detailContent = { id, back ->
                    val models by repository.models.collectAsState()
                    val model = models.firstOrNull { it.id == id }
                    ModelDetailScreen(ModelDetailUiState(id,
                        if (model == null) ModelDetailStatus.NOT_FOUND else ModelDetailStatus.CONTENT,
                        model?.let { ModelDetail(it.id, it.name, fetchedAtEpochMillis = 1) }), onBack = back)
                }) { padding, listState, click ->
                    ModelListScreen(state, onRefresh = viewModel::refresh, onDismissNotice = viewModel::clearMessage,
                        bottomOverlayPadding = padding, listState = listState, onModelClick = click)
                }
            }
        }
    }

    private class Repository : ModelRepository {
        val models = MutableStateFlow(List(40) { index ->
            ModelSummary("lab/$index", "Cached $index", price = if (index == 0) ModelPriceSummary(
                "p", "Provider", false, input = 1.0,
                tiers = listOf(ModelPriceTierSummary("context", 272_000, input = 2.0)),
            ) else null)
        })
        val remote = listOf(ModelSummary("lab/latest", "Latest"))
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        var failure: Exception? = null
        override fun observeModels() = models
        override fun observeModelDetail(id: String) = models.map { rows ->
            rows.firstOrNull { it.id == id }?.let { ModelDetail(it.id, it.name, fetchedAtEpochMillis = 1) }
        }
        override suspend fun hasLocalCatalog() = true
        override suspend fun refresh(): Boolean {
            requests++
            gate.await()
            failure?.let { throw it }
            val changed = models.value != remote
            models.value = remote
            return changed
        }
    }
}
