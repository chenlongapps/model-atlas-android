package io.github.chenlongapps.modelatlas.ui

import android.content.res.Configuration
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.chenlongapps.modelatlas.ui.navigation.GlassNavigationItem
import io.github.chenlongapps.modelatlas.ui.settings.DataSourceScreen
import io.github.chenlongapps.modelatlas.ui.settings.SettingsScreen
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailRoute
import io.github.chenlongapps.modelatlas.ui.model.LocalModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentitySnapshot
import io.github.chenlongapps.modelatlas.ui.model.ModelDetailContainer
import io.github.chenlongapps.modelatlas.ui.model.ModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelTransitionDurationMillis
import io.github.chenlongapps.modelatlas.ui.model.rememberRetainedModelVisibility
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.debug.DebugFeature
import kotlinx.coroutines.launch

@Composable
fun ModelAtlasApp(modifier: Modifier = Modifier) {
    ModelAtlasScaffold(
        modifier = modifier,
        debugContent = { bottomPadding, isVisible -> DebugFeature.Content(bottomPadding, isVisible) },
    ) { bottomOverlayPadding, listState, onModelClick ->
        ModelListRoute(bottomOverlayPadding = bottomOverlayPadding, listState = listState, onModelClick = onModelClick)
    }
}

/** 页面自行处理系统安全区；外壳共享列表滚动状态以响应导航置顶操作。 */
@Composable
internal fun ModelAtlasScaffold(
    modifier: Modifier = Modifier,
    detailContent: @Composable (modelId: String, onBack: () -> Unit) -> Unit = { id, onBack -> ModelDetailRoute(id, onBack) },
    debugContent: @Composable (bottomOverlayPadding: Dp, isVisible: Boolean) -> Unit = { _, _ -> },
    modelContent: @Composable (bottomOverlayPadding: Dp, listState: LazyListState, onModelClick: (String) -> Unit) -> Unit,
) {
    val navigationItems = remember {
        listOf(GlassNavigationItem.Models, GlassNavigationItem.Settings) + listOfNotNull(DebugFeature.navigationItem)
    }
    val pagerState = rememberPagerState(pageCount = { navigationItems.size })
    val modelListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val backdrop = rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
    val density = LocalDensity.current
    // 恢复首帧沿用已测得的高度，避免默认底部留白把接近列表末尾的保存位置夹回。
    var navigationHeight by rememberSaveable { mutableStateOf(64f) }
    val bottomOverlayPadding = navigationHeight.dp + 32.dp
    var showDataSource by rememberSaveable { mutableStateOf(false) }
    var selectedModelId by rememberSaveable { mutableStateOf<String?>(null) }
    // ID 是可保存的导航状态；身份快照仅在当前进程的页面交接期间使用。
    var retainedModelId by remember { mutableStateOf(selectedModelId) }
    var initialIdentity by remember { mutableStateOf<ModelIdentitySnapshot?>(null) }
    val mainVisibility = rememberRetainedModelVisibility(selectedModelId == null)
    val detailVisibility = rememberRetainedModelVisibility(selectedModelId != null)
    val expansionProgress by detailVisibility.transition.animateFloat(
        transitionSpec = { tween(ModelTransitionDurationMillis) }, label = "model-container-expansion",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val expansion = remember { { expansionProgress } }
    val subpageVisible = showDataSource || selectedModelId != null
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    // 设计预览没有 Activity 提供的返回事件 dispatcher。
    if (!LocalInspectionMode.current) {
        NavigationBackHandler(
            state = backState,
            isBackEnabled = subpageVisible || retainedModelId != null,
            onBackCompleted = {
                if (selectedModelId != null) selectedModelId = null else showDataSource = false
            },
        )
    }

    SharedTransitionLayout(modifier.fillMaxSize().background(background)) {
        val sharedScope = this
        val transitioning = mainVisibility.transition.currentState != mainVisibility.transition.targetState ||
            detailVisibility.transition.currentState != detailVisibility.transition.targetState ||
            mainVisibility.transition.isRunning || detailVisibility.transition.isRunning || isTransitionActive
        val placeMain = !showDataSource && (selectedModelId == null || transitioning)
        LaunchedEffect(selectedModelId, transitioning, detailVisibility.transition.currentState) {
            if (selectedModelId == null && !transitioning && detailVisibility.transition.currentState == EnterExitState.PostExit) {
                retainedModelId = null
                initialIdentity = null
            }
        }
        RetainedModelPage(placeMain, interactive = !subpageVisible) {
            CompositionLocalProvider(LocalModelSharedTransition provides ModelSharedTransition(
                sharedScope, mainVisibility, enabled = placeMain,
                expansionProgress = expansion,
                captureIdentity = { initialIdentity = it },
            )) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize().testTag("main-pager").layerBackdrop(backdrop),
                    // 保留全部顶层页，切换时不重建列表或重启刷新提示的协程。
                    beyondViewportPageCount = navigationItems.size - 1,
                    key = { navigationItems[it].tag },
                ) { page ->
                    when (page) {
                        0 -> CompositionLocalProvider(
                            LocalModelListVisible provides (!subpageVisible && pagerState.currentPage == 0),
                        ) {
                            modelContent(bottomOverlayPadding, modelListState) {
                                // 同一帧挂载详情目标，源页面一直保留到共享过渡完成。
                                retainedModelId = it
                                selectedModelId = it
                            }
                        }
                        1 -> SettingsScreen(
                            bottomOverlayPadding = bottomOverlayPadding,
                            onShowDataSource = { showDataSource = true },
                        )
                        2 -> debugContent(bottomOverlayPadding, !subpageVisible && pagerState.settledPage == page)
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
                        items = navigationItems,
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
                            navigationHeight = with(density) { it.height.toDp().value }
                        },
                    )
                }
            }
        }
        if (showDataSource) {
            DataSourceScreen(onBack = { showDataSource = false })
        }
        retainedModelId?.let { id ->
            RetainedModelPage(placed = true, interactive = selectedModelId != null) {
                CompositionLocalProvider(LocalModelSharedTransition provides ModelSharedTransition(
                    sharedScope, detailVisibility, enabled = true,
                    expansionProgress = expansion,
                    initialIdentity = initialIdentity?.takeIf { it.id == id },
                )) {
                    key(id) {
                        ModelDetailContainer(id) { detailContent(id) { selectedModelId = null } }
                    }
                }
            }
        }
    }
}

/** 退出时保留布局供容器边界匹配，但立即屏蔽旧页面的触摸与无障碍操作。 */
@Composable
private fun RetainedModelPage(
    placed: Boolean,
    interactive: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .then(if (!interactive) Modifier.clearAndSetSemantics {} else Modifier)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    if (placed) placeable.placeRelative(0, 0)
                }
            },
    ) {
        content()
        if (!interactive) Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        })
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
