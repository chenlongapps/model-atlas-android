package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.ModelAtlasScaffold
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChatNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsEntryHidesPagerAndBottomBarAndBothBackActionsRestoreSource() {
        compose.setContent { Content() }
        repeat(2) { attempt ->
            open()
            compose.onNodeWithTag("main-pager").assertIsNotDisplayed()
            compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
            if (attempt == 0) compose.onNodeWithContentDescription("返回").performClick() else pressBack()
            compose.onNodeWithTag("settings-screen").assertIsDisplayed()
            compose.onNodeWithTag("nav-settings").assertIsSelected()
        }
        compose.onNodeWithText("数据来源与许可").performClick()
        compose.onNodeWithTag("data-source-screen").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("settings-appearance").performClick()
        compose.onNodeWithTag("appearance-screen").assertIsDisplayed()
    }

    @Test fun chatReturnPreservesCatalogPositionAndCompareAndBenchmarkTabsStillWork() {
        compose.setContent { Content() }
        compose.onNodeWithTag("model-list").performScrollToNode(hasText("模型 12"))
        compose.onNodeWithTag("model-list").performTouchInput { swipe(center, center + Offset(0f, 100f), 400) }
        val before = compose.onNodeWithText("模型 12").fetchSemanticsNode().boundsInRoot.top
        open(); pressBack()
        compose.onNodeWithTag("nav-compare").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-benchmarks").performClick().assertIsSelected()
        compose.onNodeWithTag("nav-models").performClick()
        assertEquals(before, compose.onNodeWithText("模型 12").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test fun restoredChatSubpageDoesNotExposeUnderlyingNavigation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        open()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
        compose.onNodeWithTag("glass-navigation").assertIsNotDisplayed()
        pressBack()
        compose.onNodeWithTag("nav-settings").assertIsSelected()
    }

    private fun open() {
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithTag("settings-chat").performClick()
        compose.onNodeWithTag("chat-screen").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        ModelAtlasTheme(dynamicColor = false) {
            ModelAtlasScaffold { padding, list, onClick ->
                ModelListScreen(ModelListUiState(models = List(30) { ModelSummary("lab/$it", "模型 $it") }, isLoading = false), bottomOverlayPadding = padding, listState = list, onModelClick = onClick)
            }
        }
    }
}
