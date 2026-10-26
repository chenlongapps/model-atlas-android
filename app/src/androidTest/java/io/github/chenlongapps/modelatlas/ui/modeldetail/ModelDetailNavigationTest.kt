package io.github.chenlongapps.modelatlas.ui.modeldetail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelDetailNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun opensFromCardHidesMainContentAndBothBackActionsRestorePosition() {
        show()
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("Model 8"))
        val before = compose.onNodeWithTag("model:lab/8").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithText("Model 8").performClick()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithTag("model-list").assertIsNotDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("Model 8").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("model:lab/8").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithText("Model 8").performClick()
        pressBack()
        compose.onNodeWithText("Model 8").assertIsDisplayed()
    }

    @Test fun restoresSelectedIdAndDetailScrollWithoutDisplayingThePager() {
        val restoration = StateRestorationTester(compose)
        show(restoration)
        compose.onNodeWithText("Model 0").performClick()
        compose.onNodeWithTag("model-detail-scroll").performScrollToIndex(2)
        compose.onNodeWithTag("model-detail-content").performScrollToNode(hasText("参数量"))
        val before = compose.onNodeWithText("参数量").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("model-detail-screen").assertIsDisplayed()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        assertEquals(before, compose.onNodeWithText("参数量").fetchSemanticsNode().boundsInRoot.top, 1f)
        pressBack()
        compose.onNodeWithText("Model 0").assertIsDisplayed()
    }

    @Test fun tierControlDoesNotNavigateAndExpandedListPriceSurvivesDetail() {
        show()
        compose.onNodeWithContentDescription("展开档位价格").performClick()
        compose.onNodeWithTag("model-detail-screen").assertDoesNotExist()
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
        compose.onNodeWithText("Model 0").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("收起档位价格").assertIsDisplayed()
    }

    private fun show(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            ModelAtlasTheme(dynamicColor = false) {
                ModelAtlasScaffold(detailContent = { id, back ->
                    ModelDetailScreen(ModelDetailUiState(id, ModelDetailStatus.CONTENT,
                        ModelDetail(id, "Detail $id", fetchedAtEpochMillis = 100)), onBack = back)
                }) { padding, listState, onModelClick ->
                    ModelListScreen(
                        ModelListUiState(models = List(20) { index ->
                            ModelSummary("lab/$index", "Model $index", price = if (index == 0) ModelPriceSummary(
                                "p", "Provider", false, input = 1.0,
                                tiers = listOf(ModelPriceTierSummary("context", 272_000, input = 2.0)),
                            ) else null)
                        }, isLoading = false), bottomOverlayPadding = padding, listState = listState,
                        onModelClick = onModelClick,
                    )
                }
            }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }
}
