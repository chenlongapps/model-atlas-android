package io.github.chenlongapps.modelatlas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import io.github.chenlongapps.modelatlas.testing.saveRegressionScreenshot
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationBar
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationItem
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Debug 测试宿主显式传 Release 的四项胶囊，避免只验证带调试条目的布局。 */
class GlassNavigationBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun releaseNavigationAt320DpKeepsCircleAndFourTabsUsable() = assertReleaseNavigation(false)

    @Test fun releaseNavigationAt320DpLargeFontDarkRtlKeepsCircleAndFourTabsUsable() = assertReleaseNavigation(true)

    private fun assertReleaseNavigation(largeRtl: Boolean) {
        var minimumPixels = 0f
        var opens = 0
        compose.setContent {
            val density = LocalDensity.current
            minimumPixels = with(density) { 48.dp.toPx() }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, if (largeRtl) 2f else 1f),
                LocalLayoutDirection provides if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ModelAtlasTheme(darkTheme = largeRtl, dynamicColor = false) {
                    val items = listOf(GlassNavigationItem.Models, GlassNavigationItem.Benchmarks, GlassNavigationItem.Compare, GlassNavigationItem.Settings)
                    val pager = rememberPagerState(pageCount = { items.size })
                    val scope = rememberCoroutineScope()
                    val backdrop = rememberLayerBackdrop { drawContent() }
                    Box(Modifier.requiredSize(320.dp, 200.dp).background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(horizontal = 16.dp)) {
                        HorizontalPager(pager, Modifier.fillMaxSize().layerBackdrop(backdrop)) { }
                        GlassNavigationBar(
                            pagerState = pager,
                            backdrop = backdrop,
                            onNavigate = { scope.launch { pager.animateScrollToPage(it) } },
                            onOpenChat = { opens++ },
                            onModelsDoubleClick = {},
                            items = items,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        val ai = compose.onNodeWithTag("nav-chat").fetchSemanticsNode().boundsInRoot
        val capsule = compose.onNodeWithTag("glass-navigation-capsule").fetchSemanticsNode().boundsInRoot
        assertEquals(ai.width, ai.height, 1f)
        assertTrue(if (largeRtl) ai.left > capsule.right else ai.right < capsule.left)
        for (tag in listOf("nav-chat", "nav-models", "nav-benchmarks", "nav-compare", "nav-settings")) {
            val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag width < 48dp", bounds.width >= minimumPixels - 1f)
            assertTrue("$tag height < 48dp", bounds.height >= minimumPixels - 1f)
        }
        compose.onNodeWithTag("nav-debug").assertDoesNotExist()
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-chat").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        compose.onNodeWithTag("nav-models").assertIsSelected()
        compose.onNodeWithTag("nav-indicator").assertIsDisplayed()
        saveRegressionScreenshot("navigation-release-${if (largeRtl) "large-dark-rtl" else "light"}-ai-button")
        compose.onNodeWithTag("nav-settings").performClick().assertIsSelected()
        val indicator = compose.onNodeWithTag("nav-indicator").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val settings = compose.onNodeWithTag("nav-settings").fetchSemanticsNode().boundsInRoot
        assertEquals(settings.center.x, indicator.center.x, 1f)
        saveRegressionScreenshot("navigation-release-${if (largeRtl) "large-dark-rtl" else "light"}-settings")
    }
}
