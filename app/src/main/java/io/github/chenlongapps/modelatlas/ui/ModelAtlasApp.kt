package io.github.chenlongapps.modelatlas.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListRoute
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationBar
import io.github.chenlongapps.modelatlas.ui.settings.SettingsScreen
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

@Composable
fun ModelAtlasApp(modifier: Modifier = Modifier) {
    ModelAtlasScaffold(modifier = modifier) { bottomOverlayPadding ->
        ModelListRoute(bottomOverlayPadding = bottomOverlayPadding)
    }
}

/** 页面保留独立状态与系统安全区；这里只传递悬浮栏额外占用的高度。 */
@Composable
internal fun ModelAtlasScaffold(
    modifier: Modifier = Modifier,
    modelContent: @Composable (bottomOverlayPadding: Dp) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val density = LocalDensity.current
    var navigationHeight by remember { mutableStateOf(64.dp) }
    val bottomOverlayPadding = navigationHeight + 32.dp

    Box(modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().testTag("main-pager").layerBackdrop(backdrop),
            // 两个页面都保留，切换时不重建列表，也不重启刷新提示的协程。
            beyondViewportPageCount = 1,
            key = { it },
        ) { page ->
            when (page) {
                0 -> modelContent(bottomOverlayPadding)
                1 -> SettingsScreen(bottomOverlayPadding = bottomOverlayPadding)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth(),
        ) {
            GlassNavigationBar(
                pagerState = pagerState,
                backdrop = backdrop,
                onNavigate = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                modifier = Modifier.fillMaxWidth().onSizeChanged {
                    navigationHeight = with(density) { it.height.toDp() }
                },
            )
        }
    }
}

@Preview(name = "Glass navigation · Light", showBackground = true)
@Preview(name = "Glass navigation · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Glass navigation · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelAtlasAppPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        ModelAtlasScaffold { bottomPadding ->
            ModelListScreen(ModelListUiState(isLoading = false), bottomOverlayPadding = bottomPadding)
        }
    }
}
