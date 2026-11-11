package io.github.chenlongapps.modelatlas.ui

import android.content.res.Configuration
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.navigationevent.NavigationEventTransitionState
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
import io.github.chenlongapps.modelatlas.ui.navigation.NavigationScrollConnection
import io.github.chenlongapps.modelatlas.ui.navigation.NavigationScrollState
import io.github.chenlongapps.modelatlas.ui.navigation.rememberTouchExplorationEnabled
import io.github.chenlongapps.modelatlas.ui.settings.DataSourceScreen
import io.github.chenlongapps.modelatlas.ui.settings.SettingsScreen
import io.github.chenlongapps.modelatlas.ui.settings.AppearanceScreen
import io.github.chenlongapps.modelatlas.ui.settings.AppearanceUiState
import io.github.chenlongapps.modelatlas.ui.modeldetail.ModelDetailRoute
import io.github.chenlongapps.modelatlas.ui.model.LocalModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentitySnapshot
import io.github.chenlongapps.modelatlas.ui.model.ModelDetailContainer
import io.github.chenlongapps.modelatlas.ui.model.ModelDetailTransitionController
import io.github.chenlongapps.modelatlas.ui.model.ModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelTransitionDurationMillis
import io.github.chenlongapps.modelatlas.ui.transition.rememberRetainedPageVisibility
import io.github.chenlongapps.modelatlas.ui.transition.RetainedPageTransitionController
import io.github.chenlongapps.modelatlas.ui.transition.SharedPageTransitionDurationMillis
import io.github.chenlongapps.modelatlas.ui.benchmark.LocalBenchmarkSharedTransition
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkSharedTransition
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkLeaderboardContainer
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkRoute
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkScreen
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkLeaderboardRoute
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkLeaderboardScreen
import io.github.chenlongapps.modelatlas.ui.benchmark.BenchmarkUiState
import io.github.chenlongapps.modelatlas.ui.debug.DebugFeature
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareRoute
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareScreen
import io.github.chenlongapps.modelatlas.ui.modelcompare.ModelCompareUiState
import io.github.chenlongapps.modelatlas.ui.favorites.FavoritesScreen
import io.github.chenlongapps.modelatlas.ui.favorites.FavoritesUiState
import io.github.chenlongapps.modelatlas.ui.favorites.FavoriteRemovalTransition
import io.github.chenlongapps.modelatlas.ui.favorites.LocalFavoriteRemovalTransition
import kotlinx.coroutines.launch

@Composable
fun ModelAtlasApp(
    modifier: Modifier = Modifier,
    appearanceContent: @Composable (onBack: () -> Unit) -> Unit = { onBack ->
        AppearanceScreen(AppearanceUiState(isLoading = false, startupComplete = true), onBack)
    },
) {
    ModelAtlasScaffold(
        modifier = modifier,
        appearanceContent = appearanceContent,
        debugContent = { bottomPadding, isVisible, onOpen -> DebugFeature.Content(bottomPadding, isVisible, onOpen) },
        databaseContent = { onClose -> DebugFeature.DatabaseContent(onClose) },
        compareContent = { bottomPadding, isVisible -> ModelCompareRoute(bottomPadding, isVisible) },
        benchmarkContent = { bottomPadding, isVisible, onOpen -> BenchmarkRoute(bottomPadding, isVisible, onOpen) },
        leaderboardContent = { id, onBack -> BenchmarkLeaderboardRoute(id, onBack) },
    ) { bottomOverlayPadding, listState, onModelClick ->
        ModelListRoute(bottomOverlayPadding = bottomOverlayPadding, listState = listState, onModelClick = onModelClick)
    }
}

/** 页面自行处理系统安全区；外壳共享列表滚动状态以响应导航置顶操作。 */
@Composable
internal fun ModelAtlasScaffold(
    modifier: Modifier = Modifier,
    detailContent: @Composable (modelId: String, onBack: () -> Unit) -> Unit = { id, onBack -> ModelDetailRoute(id, onBack) },
    debugContent: @Composable (bottomOverlayPadding: Dp, isVisible: Boolean, onOpenDatabase: () -> Unit) -> Unit = { _, _, _ -> },
    databaseContent: @Composable (onClose: () -> Unit) -> Unit = {},
    benchmarkContent: @Composable (bottomOverlayPadding: Dp, isVisible: Boolean, onOpen: (String) -> Unit) -> Unit = { padding, _, onOpen ->
        BenchmarkScreen(BenchmarkUiState(isLoading = false), onOpen, bottomOverlayPadding = padding)
    },
    leaderboardContent: @Composable (boardId: String, onBack: () -> Unit) -> Unit = { id, onBack ->
        BenchmarkLeaderboardScreen(BenchmarkUiState(isLoading = false), id, onBack)
    },
    compareContent: @Composable (bottomOverlayPadding: Dp, isVisible: Boolean) -> Unit = { padding, visible ->
        ModelCompareScreen(ModelCompareUiState(), bottomOverlayPadding = padding, isVisible = visible)
    },
    favoritesContent: @Composable (bottomOverlayPadding: Dp, onModelClick: (String) -> Unit) -> Unit = { padding, onClick ->
        FavoritesScreen(FavoritesUiState(isLoading = false), onClick, bottomOverlayPadding = padding)
    },
    appearanceContent: @Composable (onBack: () -> Unit) -> Unit = { onBack ->
        AppearanceScreen(AppearanceUiState(isLoading = false, startupComplete = true), onBack)
    },
    touchExplorationEnabled: Boolean = rememberTouchExplorationEnabled(),
    modelContent: @Composable (bottomOverlayPadding: Dp, listState: LazyListState, onModelClick: (String) -> Unit) -> Unit,
) {
    val navigationItems = remember {
        listOf(GlassNavigationItem.Models, GlassNavigationItem.Benchmarks, GlassNavigationItem.Compare, GlassNavigationItem.Settings) + listOfNotNull(DebugFeature.navigationItem)
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
    val scrollThreshold = with(density) { 12.dp.toPx() }
    val navigationScroll = remember(scrollThreshold) { NavigationScrollState(scrollThreshold) }
    val autoHideEnabled = !touchExplorationEnabled
    val navigationHidden = autoHideEnabled && navigationScroll.isHidden
    val navigationHideProgress = animateFloatAsState(
        if (navigationHidden) 1f else 0f, tween(200), label = "Navigation scroll visibility",
    )
    LaunchedEffect(pagerState, navigationScroll, autoHideEnabled) {
        snapshotFlow { pagerState.currentPage to (pagerState.currentPageOffsetFraction != 0f) }.collect {
            // 只响应真实横移：纵向列表松手也可能让 Pager 短暂进入零位移 fling。
            navigationScroll.show()
        }
    }
    // 恢复首帧沿用已测得的高度，避免默认底部留白把接近列表末尾的保存位置夹回。
    var navigationHeight by rememberSaveable { mutableStateOf(64f) }
    val bottomOverlayPadding = navigationHeight.dp + 32.dp
    var showDataSource by rememberSaveable { mutableStateOf(false) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    var showDatabase by rememberSaveable { mutableStateOf(false) }
    var selectedBenchmarkId by rememberSaveable { mutableStateOf<String?>(null) }
    var retainedBenchmarkId by remember { mutableStateOf(selectedBenchmarkId) }
    val benchmarkTransition = remember { RetainedPageTransitionController(selectedBenchmarkId != null) }
    val benchmarkMainVisibility = rememberRetainedPageVisibility(benchmarkTransition.main, benchmarkTransition::onMainTargetComposed)
    val leaderboardVisibility = rememberRetainedPageVisibility(benchmarkTransition.detail, benchmarkTransition::onDetailTargetComposed)
    LaunchedEffect(benchmarkTransition) { benchmarkTransition.run { selectedBenchmarkId = null } }
    val benchmarkExpansionProgress by leaderboardVisibility.transition.animateFloat(
        transitionSpec = { tween(SharedPageTransitionDurationMillis) }, label = "benchmark-container-expansion",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val benchmarkExpansion = remember { { benchmarkExpansionProgress } }
    val leaderboardStates = rememberSaveableStateHolder()
    var selectedModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailSourceTag by rememberSaveable { mutableStateOf(GlassNavigationItem.Models.tag) }
    // ID 是可保存的导航状态；身份快照仅在当前进程的页面交接期间使用。
    var retainedModelId by remember { mutableStateOf(selectedModelId) }
    var initialIdentity by remember { mutableStateOf<ModelIdentitySnapshot?>(null) }
    val favoriteRemoval = remember { FavoriteRemovalTransition() }
    val detailTransition = remember { ModelDetailTransitionController(selectedModelId != null) }
    val mainVisibility = rememberRetainedPageVisibility(detailTransition.main, detailTransition::onMainTargetComposed)
    val detailVisibility = rememberRetainedPageVisibility(detailTransition.detail, detailTransition::onDetailTargetComposed)
    LaunchedEffect(detailTransition) {
        detailTransition.run { selectedModelId = null }
    }
    val expansionProgress by detailVisibility.transition.animateFloat(
        transitionSpec = { tween(ModelTransitionDurationMillis) }, label = "model-container-expansion",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    val expansion = remember { { expansionProgress } }
    val subpageVisible = showDataSource || showAppearance || showDatabase || retainedBenchmarkId != null || retainedModelId != null
    LaunchedEffect(subpageVisible, navigationScroll) { navigationScroll.resetTracking() }
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    LaunchedEffect(backState, detailTransition, benchmarkTransition) {
        snapshotFlow { backState.transitionState }.collect { state ->
            if (state is NavigationEventTransitionState.InProgress && selectedModelId != null) {
                detailTransition.seekBack(state.latestEvent.progress)
            } else if (state is NavigationEventTransitionState.InProgress && selectedBenchmarkId != null) {
                benchmarkTransition.seekBack(state.latestEvent.progress)
            }
            // Idle 不重置动画；取消／确认回调明确决定结算方向。
        }
    }
    // 设计预览没有 Activity 提供的返回事件 dispatcher。
    if (!LocalInspectionMode.current) {
        NavigationBackHandler(
            state = backState,
            isBackEnabled = subpageVisible && !showDatabase,
            onBackCancelled = {
                if (retainedBenchmarkId != null) benchmarkTransition.cancelBack() else detailTransition.cancelBack()
            },
            onBackCompleted = {
                if (retainedModelId != null) detailTransition.exit()
                else if (retainedBenchmarkId != null) benchmarkTransition.exit()
                else if (showAppearance) showAppearance = false
                else showDataSource = false
            },
        )
    }

    SharedTransitionLayout(modifier.fillMaxSize().background(background)) {
        val sharedScope = this
        val transitioning = detailTransition.active || mainVisibility.transition.currentState != mainVisibility.transition.targetState ||
            detailVisibility.transition.currentState != detailVisibility.transition.targetState ||
            mainVisibility.transition.isRunning || detailVisibility.transition.isRunning || isTransitionActive
        val benchmarkTransitioning = benchmarkTransition.active ||
            benchmarkMainVisibility.transition.currentState != benchmarkMainVisibility.transition.targetState ||
            leaderboardVisibility.transition.currentState != leaderboardVisibility.transition.targetState ||
            benchmarkMainVisibility.transition.isRunning || leaderboardVisibility.transition.isRunning || isTransitionActive
        val placeMain = !showDataSource && !showAppearance && !showDatabase &&
            (selectedBenchmarkId == null || benchmarkTransitioning) && (selectedModelId == null || transitioning)
        LaunchedEffect(selectedBenchmarkId, benchmarkTransitioning, leaderboardVisibility.transition.currentState) {
            if (selectedBenchmarkId == null && !benchmarkTransitioning &&
                leaderboardVisibility.transition.currentState == EnterExitState.PostExit) {
                // 交接后丢弃本次阅读状态；不在退出中回顶，重建／返回取消仍保留位置。
                retainedBenchmarkId?.let(leaderboardStates::removeState)
                retainedBenchmarkId = null
            }
        }
        LaunchedEffect(selectedModelId, transitioning, detailVisibility.transition.currentState) {
            if (selectedModelId == null && !transitioning && detailVisibility.transition.currentState == EnterExitState.PostExit) {
                // 共享覆盖层也完成交接后，才允许收藏来源卡片开始自己的退出动画。
                favoriteRemoval.finishReturn()
                retainedModelId = null
                initialIdentity = null
            }
        }
        RetainedPage(placeMain, interactive = !subpageVisible) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("main-pager").layerBackdrop(backdrop),
                // 保留全部顶层页，切换时不重建列表或重启刷新提示的协程。
                beyondViewportPageCount = navigationItems.size - 1,
                key = { navigationItems[it].tag },
            ) { page ->
                val sourceTag = navigationItems[page].tag
                val scrollConnection = remember(page, navigationScroll, autoHideEnabled, subpageVisible) {
                    NavigationScrollConnection(navigationScroll) {
                        autoHideEnabled && !subpageVisible && !pagerState.isScrollInProgress && pagerState.settledPage == page
                    }
                }
                Box(Modifier.fillMaxSize().nestedScroll(scrollConnection)) {
                    // 离屏页仍保留组合，但只有真实来源注册共享元素，避免同 ID 的两张卡竞争匹配。
                    CompositionLocalProvider(LocalModelSharedTransition provides ModelSharedTransition(
                        sharedScope, mainVisibility,
                        enabled = placeMain && if (retainedModelId != null) sourceTag == detailSourceTag else page == pagerState.currentPage,
                        expansionProgress = expansion,
                        captureIdentity = { if (retainedModelId == null) initialIdentity = it },
                    )) {
                        val openModel: (String) -> Unit = { id ->
                            // 同一帧挂载详情目标，源页面一直保留到共享过渡完成。
                            if (retainedModelId == null) {
                                detailSourceTag = sourceTag
                                retainedModelId = id
                                selectedModelId = id
                                detailTransition.enter()
                            }
                        }
                        when (navigationItems[page]) {
                            GlassNavigationItem.Models -> CompositionLocalProvider(
                                LocalModelListVisible provides (!subpageVisible && pagerState.currentPage == page),
                                LocalFavoriteRemovalTransition provides favoriteRemoval,
                            ) {
                                modelContent(bottomOverlayPadding, modelListState, openModel)
                            }
                            GlassNavigationItem.Compare -> compareContent(bottomOverlayPadding, !subpageVisible && pagerState.settledPage == page)
                            GlassNavigationItem.Benchmarks -> CompositionLocalProvider(
                                LocalBenchmarkSharedTransition provides BenchmarkSharedTransition(
                                    sharedScope, benchmarkMainVisibility, enabled = placeMain,
                                    expansionProgress = benchmarkExpansion,
                                ),
                            ) {
                                benchmarkContent(
                                    bottomOverlayPadding,
                                    pagerState.settledPage == page && (!subpageVisible || retainedBenchmarkId != null),
                                ) { id ->
                                    if (!subpageVisible && retainedBenchmarkId == null) {
                                        retainedBenchmarkId = id
                                        selectedBenchmarkId = id
                                        benchmarkTransition.enter()
                                    }
                                }
                            }
                            GlassNavigationItem.Settings -> SettingsScreen(
                                bottomOverlayPadding = bottomOverlayPadding,
                                onShowDataSource = { showDataSource = true },
                                onShowAppearance = { showAppearance = true },
                            )
                            else -> debugContent(bottomOverlayPadding, !subpageVisible && pagerState.settledPage == page) { showDatabase = true }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // 整个外壳（含底部安全区）滑出，列表留白与测量高度保持不变。
                    .graphicsLayer { translationY = size.height * navigationHideProgress.value }
                    .testTag("glass-navigation-motion")
                    .then(if (navigationHidden) Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {} else Modifier)
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
                    enabled = !navigationHidden,
                    modifier = Modifier.fillMaxWidth().onSizeChanged {
                        navigationHeight = with(density) { it.height.toDp().value }
                    },
                )
            }
        }
        if (showDataSource) {
            DataSourceScreen(onBack = { showDataSource = false })
        }
        if (showAppearance) {
            appearanceContent { showAppearance = false }
        }
        if (showDatabase) {
            databaseContent { showDatabase = false }
        }
        retainedBenchmarkId?.let { id ->
            RetainedPage(placed = true, interactive = selectedBenchmarkId != null && benchmarkTransition.interactive) {
                CompositionLocalProvider(LocalBenchmarkSharedTransition provides BenchmarkSharedTransition(
                    sharedScope, leaderboardVisibility, enabled = true, expansionProgress = benchmarkExpansion,
                )) {
                    leaderboardStates.SaveableStateProvider(id) {
                        BenchmarkLeaderboardContainer(id) { leaderboardContent(id, benchmarkTransition::exit) }
                    }
                }
            }
        }
        retainedModelId?.let { id ->
            RetainedPage(placed = true, interactive = selectedModelId != null && detailTransition.interactive) {
                CompositionLocalProvider(LocalModelSharedTransition provides ModelSharedTransition(
                    sharedScope, detailVisibility, enabled = true,
                    expansionProgress = expansion,
                    initialIdentity = initialIdentity?.takeIf { it.id == id },
                ), LocalFavoriteRemovalTransition provides favoriteRemoval) {
                    key(id) {
                        ModelDetailContainer(id) { detailContent(id, detailTransition::exit) }
                    }
                }
            }
        }
    }
}

/** 退出时保留布局供容器边界匹配，但立即屏蔽旧页面的触摸与无障碍操作。 */
@Composable
private fun RetainedPage(
    placed: Boolean,
    interactive: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .then(if (!interactive) Modifier.focusProperties { canFocus = false }.clearAndSetSemantics {} else Modifier)
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
