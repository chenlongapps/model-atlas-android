package io.github.chenlongapps.modelatlas.ui.settings

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val enabled = mutableStateOf(false)
    private val canEdit = mutableStateOf(true)
    private val readError = mutableStateOf(false)
    private var changes = 0

    @Test fun defaultsToOffAndWholeRowTogglesExactlyOnce() {
        show()
        compose.onNodeWithTag("settings-navigation-auto-hide").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(1, changes) }
        compose.onNodeWithTag("settings-navigation-auto-hide").performClick().assertIsOff()
        compose.runOnIdle { assertEquals(2, changes) }
    }

    @Test fun loadingDisablesToggleAndReadErrorOffersRetry() {
        canEdit.value = false
        show()
        compose.onNodeWithTag("settings-navigation-auto-hide").assertIsNotEnabled().assertIsOff()
        compose.runOnIdle { readError.value = true }
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("settings-navigation-auto-hide").assertIsEnabled().performClick().assertIsOn()
    }

    @Test fun darkNarrowDoubleFontRtlKeepsFullTextAndTouchTarget() {
        show(dark = true, largeRtl = true)
        assertLabels()
        compose.onNodeWithTag("settings-navigation-auto-hide").performClick().assertIsOn()
    }

    @Test fun dynamicColorsKeepLabelsAndToggleUsable() {
        show(dynamic = true)
        assertLabels()
        compose.onNodeWithTag("settings-navigation-auto-hide").performClick().assertIsOn()
    }

    private fun assertLabels() {
        for (text in listOf("底部导航栏自动隐藏", "向下滚动时隐藏，反向滚动时显示")) {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val layout = results.single()
            assertFalse(layout.didOverflowHeight)
            repeat(layout.lineCount) { line ->
                assertTrue(layout.getLineLeft(line) >= -1f)
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                assertFalse(layout.isLineEllipsized(line))
            }
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
        }
        val bounds = compose.onNodeWithTag("settings-navigation-auto-hide").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            with(compose.density) {
                assertTrue(bounds.width >= 48.dp.toPx())
                assertTrue(bounds.height >= 48.dp.toPx())
            }
        }
    }

    private fun show(dark: Boolean = false, dynamic: Boolean = false, largeRtl: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (largeRtl) 2f else 1f),
                LocalLayoutDirection provides if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ModelAtlasTheme(darkTheme = dark, dynamicColor = dynamic) {
                    SettingsScreen(
                        bottomOverlayPadding = 96.dp,
                        onShowDataSource = {},
                        modifier = if (largeRtl) Modifier.requiredWidth(320.dp) else Modifier,
                        navigationAutoHideEnabled = enabled.value,
                        onNavigationAutoHideChange = { enabled.value = it; changes++ },
                        canEdit = canEdit.value,
                        hasReadError = readError.value,
                        onRetry = { readError.value = false; canEdit.value = true },
                    )
                }
            }
        }
    }
}
