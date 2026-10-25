package io.github.chenlongapps.modelatlas.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearanceHidesNavigationAndButtonBackRestoresSettings() {
        compose.setContent { Content() }
        openAppearance()
        compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithTag("settings-screen").assertIsDisplayed()
    }

    @Test fun systemBackKeepsCatalogPositionAndSourceEntryStillWorks() {
        compose.setContent { Content() }
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("模型 12"))
        val before = compose.onNodeWithText("模型 12").fetchSemanticsNode().boundsInRoot.top
        openAppearance()
        pressBack()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithTag("data-source-screen").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun appearanceSubpageAndScrollRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        openAppearance()
        compose.onNodeWithTag("appearance-content").performScrollToNode(hasText("效果预览"))
        val before = compose.onNodeWithText("效果预览").fetchSemanticsNode().boundsInRoot.top
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("appearance-screen").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText("效果预览").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }

    private fun openAppearance() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.onNodeWithTag("appearance-screen").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        ModelAtlasTheme(dynamicColor = false) {
            ModelAtlasScaffold { padding, listState, onClick ->
                ModelListScreen(
                    ModelListUiState(models = List(30) { ModelSummary("lab/$it", "模型 $it") }, isLoading = false),
                    bottomOverlayPadding = padding,
                    listState = listState,
                    onModelClick = onClick,
                )
            }
        }
    }
}
