package io.github.chenlongapps.modelatlas.ui.modellist

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ModelCardFavoriteLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun favoriteMarkerSharesHeaderAndCardRemainsClickable() {
        var opened = false
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                ModelCard(ModelSummary("lab/a", "Alpha"), false, {}, modifier = Modifier.width(320.dp),
                    onOpenDetail = { opened = true }, isFavorite = true)
            }
        }
        val name = compose.onNodeWithTag("model-name", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val marker = compose.onNodeWithTag("model-favorite:lab/a", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(name.top.value, marker.top.value, 1f)
        assertTrue(name.right <= marker.left)
        compose.onNodeWithContentDescription("已收藏", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("model:lab/a").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun longNameAndMarkerFitNarrowLargeFontRtl() {
        val name = "Long model name with several words that must remain fully visible"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f),
                LocalLayoutDirection provides LayoutDirection.Rtl) {
                ModelAtlasTheme(dynamicColor = false) {
                    ModelCard(ModelSummary("lab/a", name), false, {}, modifier = Modifier.width(320.dp), isFavorite = true)
                }
            }
        }
        val nameNode = compose.onNodeWithTag("model-name", useUnmergedTree = true)
        val nameBounds = nameNode.getUnclippedBoundsInRoot()
        val marker = compose.onNodeWithTag("model-favorite:lab/a", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(nameBounds.top.value, marker.top.value, 1f)
        assertTrue(marker.right <= nameBounds.left)
        val layouts = mutableListOf<TextLayoutResult>()
        nameNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(name, layouts.single().layoutInput.text.text)
        assertTrue(layouts.single().lineCount > 1)
        assertFalse(layouts.single().didOverflowWidth)
        assertFalse(layouts.single().didOverflowHeight)
    }
}
