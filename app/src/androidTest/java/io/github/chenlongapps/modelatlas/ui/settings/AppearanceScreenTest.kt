package io.github.chenlongapps.modelatlas.ui.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.pressBack
import io.github.chenlongapps.modelatlas.data.model.ThemeColorSource
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppearanceScreenTest {
    @get:Rule val compose = createComposeRule()
    private val state = mutableStateOf(AppearanceUiState(isLoading = false, startupComplete = true))
    private var flushes = 0

    @Test fun modeAndAllPaletteOptionsHaveSelectionSemanticsWithoutVisibleColorNames() {
        show()
        compose.onNodeWithTag("theme-mode-System").assertIsSelected()
        compose.onNodeWithTag("color-system").assertIsSelected()
        compose.onNodeWithTag("theme-mode-Dark").performClick().assertIsSelected()
        compose.onNodeWithTag("color-preset-Blue").performClick().assertIsSelected()
        compose.onNodeWithTag("color-system").assertIsNotSelected()
        compose.onNodeWithContentDescription("预设配色 1").assertExists()
        compose.onNodeWithText("预设配色 1").assertDoesNotExist()
        compose.onNodeWithTag("appearance-content").performScrollToNode(hasTestTag("color-custom"))
        compose.onNodeWithTag("color-preset-Purple").assertExists()
        compose.onNodeWithTag("appearance-content").performScrollToNode(hasText("效果预览"))
        compose.onNodeWithText("探索模型").assertIsDisplayed()
    }

    @Test fun openingFirstPickerDoesNotChangeColorsAndCloseDoesNotUndoChanges() {
        show()
        openPicker()
        compose.runOnIdle { assertEquals(ThemeColorSource.System, state.value.appearance.colorSource) }
        setHue(123f)
        compose.runOnIdle {
            assertEquals(ThemeColorSource.Custom, state.value.appearance.colorSource)
            assertEquals(123f, state.value.appearance.customHue)
        }
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithTag("hue-picker").assertDoesNotExist()
        compose.onNodeWithTag("color-custom").assertIsSelected()
        compose.runOnIdle { assertTrue(flushes > 0) }
        compose.onNodeWithTag("appearance-content").performScrollToNode(hasTestTag("color-system"))
        compose.onNodeWithTag("color-system").performClick()
        openPicker()
        compose.runOnIdle {
            assertEquals(ThemeColorSource.Custom, state.value.appearance.colorSource)
            assertEquals(123f, state.value.appearance.customHue)
        }
    }

    @Test fun systemBackClosesPickerWithoutDiscardingColor() {
        show()
        openPicker()
        setHue(270f)
        pressBack()
        compose.onNodeWithTag("hue-picker").assertDoesNotExist()
        compose.onNodeWithTag("appearance-screen").assertIsDisplayed()
        compose.runOnIdle { assertEquals(270f, state.value.appearance.customHue) }
    }

    @Test fun pickerSurvivesStateRestorationWithoutActivatingUneditedColor() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        openPicker()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("hue-picker").assertIsDisplayed()
        compose.runOnIdle { assertEquals(ThemeColorSource.System, state.value.appearance.colorSource) }
    }

    @Test fun readFailureDisablesChoicesAndRetryRestoresEditing() {
        state.value = state.value.copy(hasReadError = true)
        var retries = 0
        show(onRetry = { retries++; state.value = state.value.copy(hasReadError = false) })
        compose.onNodeWithTag("theme-mode-Light").assertIsNotEnabled()
        compose.onNodeWithTag("color-system").assertIsNotEnabled()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("theme-mode-Light").assertIsEnabled()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun narrowLargeFontRtlKeepsLabelsAndTouchTargetsUsable() {
        show(large = true)
        for (text in listOf("浅色", "深色", "跟随系统")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            // Row 中段落测量宽度可能大于最终文字宽度；核对实际每行边界，不能只看 didOverflowWidth。
            repeat(layout.lineCount) { line ->
                assertTrue("$text left edge", layout.getLineLeft(line) >= -1f)
                assertTrue("$text right edge", layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse(layout.isLineEllipsized(line))
            }
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
        compose.onNodeWithTag("appearance-content").performScrollToNode(hasTestTag("color-custom"))
        val tile = compose.onNodeWithTag("color-custom").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val density = compose.density.density
            assertTrue(tile.width >= 48f * density)
            assertTrue(tile.height >= 48f * density)
        }
        compose.onNodeWithTag("color-custom").performClick()
        compose.onNodeWithTag("hue-slider").assertIsDisplayed()
        setHue(90f)
        compose.onNodeWithContentDescription("关闭").performClick()
        compose.onNodeWithTag("color-custom").assertIsSelected()
    }

    private fun show(large: Boolean = false, onRetry: () -> Unit = {}) {
        compose.setContent { Content(large, onRetry) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(large: Boolean = false, onRetry: () -> Unit = {}) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, if (large) 2f else 1f),
            LocalLayoutDirection provides if (large) LayoutDirection.Rtl else LayoutDirection.Ltr,
        ) {
            ModelAtlasTheme(dynamicColor = false, appearance = state.value.appearance) {
                AppearanceScreen(
                    uiState = state.value,
                    onBack = {},
                    modifier = if (large) Modifier.requiredWidth(320.dp) else Modifier,
                    onMode = { mode -> state.value = state.value.copy(appearance = state.value.appearance.copy(mode = mode)) },
                    onSystemColors = { state.value = state.value.copy(appearance = state.value.appearance.copy(colorSource = ThemeColorSource.System)) },
                    onPreset = { preset -> state.value = state.value.copy(appearance = state.value.appearance.copy(colorSource = ThemeColorSource.Preset, preset = preset)) },
                    onRestoreCustom = {
                        if (state.value.appearance.customHue != null) state.value = state.value.copy(appearance = state.value.appearance.copy(colorSource = ThemeColorSource.Custom))
                    },
                    onHue = { hue -> state.value = state.value.copy(appearance = state.value.appearance.copy(colorSource = ThemeColorSource.Custom, customHue = hue)) },
                    onFlush = { flushes++ },
                    onRetry = onRetry,
                )
            }
        }
    }

    private fun openPicker() {
        compose.onNodeWithTag("appearance-content").performScrollToNode(hasTestTag("color-custom"))
        compose.onNodeWithTag("color-custom").performClick()
        compose.onNodeWithTag("hue-picker").assertIsDisplayed()
    }

    private fun setHue(hue: Float) {
        compose.onNodeWithTag("hue-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(hue) }
    }
}
