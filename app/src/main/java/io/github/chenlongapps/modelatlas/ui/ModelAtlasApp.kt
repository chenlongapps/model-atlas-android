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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.chenlongapps.modelatlas.ui.modellist.LocalModelListVisible
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListRoute
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListScreen
import io.github.chenlongapps.modelatlas.ui.modellist.ModelListUiState
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationBar
import io.github.chenlongapps.modelatlas.ui.settings.DataSourceScreen
import io.github.chenlongapps.modelatlas.ui.settings.SettingsScreen
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailRoute
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import kotlinx.coroutines.launch

@Composable
fun ModelAtlasApp(modifier: Modifier = Modifier) {
    ModelAtlasScaffold(modifier = modifier) { bottomOverlayPadding, listState, onModelClick ->
        ModelListRoute(bottomOverlayPadding = bottomOverlayPadding, listState = listState, onModelClick = onModelClick)
    }
}

/** 页面自行处理系统安全区；外壳共享列表滚动状态以响应导航置顶操作。 */
@Composable
internal fun ModelAtlasScaffold(
    modifier: Modifier = Modifier,
    detailContent: @Composable (modelId: String, onBack: () -> Unit) -> Unit = { id, onBack -> ModelDetailRoute(id, onBack) },
    modelContent: @Composable (bottomOverlayPadding: Dp, listState: LazyListState, onModelClick: (String) -> Unit) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val modelListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val density = LocalDensity.current
    var navigationHeight by remember { mutableStateOf(64.dp) }
    val bottomOverlayPadding = navigationHeight + 32.dp
    var showDataSource by rememberSaveable { mutableStateOf(false) }
    var selectedModelId by rememberSaveable { mutableStateOf<String?>(null) }
    val subpageVisible = showDataSource || selectedModelId != null
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // 设计预览没有 Activity 提供的返回事件 dispatcher。
    if (!LocalInspectionMode.current) {
        NavigationBackHandler(
            state = backState,
            isBackEnabled = subpageVisible,
            onBackCompleted = {
                if (selectedModelId != null) selectedModelId = null else showDataSource = false
            },
        )
    }

    Box(modifier.fillMaxSize()) {
        // 保留分页器的组合与状态，但子页面打开时不放置主界面，避免绘制、触摸和读屏穿透。
        Box(
            Modifier.fillMaxSize()
                .then(if (subpageVisible) Modifier.clearAndSetSemantics {} else Modifier)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        if (!subpageVisible) placeable.placeRelative(0, 0)
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("main-pager").layerBackdrop(backdrop),
                // 两个页面都保留，切换时不重建列表，也不重启刷新提示的协程。
                beyondViewportPageCount = 1,
                key = { it },
            ) { page ->
                when (page) {
                    0 -> CompositionLocalProvider(
                        LocalModelListVisible provides (!subpageVisible && pagerState.currentPage == 0),
                    ) {
                        modelContent(bottomOverlayPadding, modelListState) { selectedModelId = it }
                    }
                    1 -> SettingsScreen(
                        bottomOverlayPadding = bottomOverlayPadding,
                        onShowDataSource = { showDataSource = true },
                    )
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
                    onModelsDoubleClick = {
                        scope.launch {
                            // 切页与回顶同时进行，不在模型页出现后再瞬间跳到页头。
                            launch { pagerState.animateScrollToPage(0) }
                            modelListState.animateScrollToItem(0, 0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().onSizeChanged {
                        navigationHeight = with(density) { it.height.toDp() }
                    },
                )
            }
        }
        if (showDataSource) {
            DataSourceScreen(onBack = { showDataSource = false })
        }
        selectedModelId?.let { id ->
            key(id) { detailContent(id) { selectedModelId = null } }
        }
    }
}

@Preview(name = "Glass navigation · Light", showBackground = true)
@Preview(name = "Glass navigation · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Glass navigation · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelAtlasAppPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        ModelAtlasScaffold { bottomPadding, listState, onModelClick ->
            ModelListScreen(
                ModelListUiState(isLoading = false),
                bottomOverlayPadding = bottomPadding,
                listState = listState,
                onModelClick = onModelClick,
            )
        }
    }
}
