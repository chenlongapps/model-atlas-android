package io.github.chenlongapps.modelatlas.ui.modeldetail

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.model.LocalModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentitySnapshot
import io.github.chenlongapps.modelatlas.ui.favorites.FavoriteActionState
import io.github.chenlongapps.modelatlas.ui.favorites.FavoriteDetailViewModel
import io.github.chenlongapps.modelatlas.ui.favorites.FavoriteHistoricalIdentity
import io.github.chenlongapps.modelatlas.ui.favorites.LocalFavoriteRemovalTransition
import kotlinx.coroutines.launch

@Composable
internal fun ModelDetailRoute(modelId: String, onBack: () -> Unit, viewModel: ModelDetailViewModel = hiltViewModel(),
    favoriteViewModel: FavoriteDetailViewModel = hiltViewModel()) {
    DisposableEffect(viewModel, modelId) {
        viewModel.open(modelId)
        onDispose { viewModel.close(modelId) }
    }
    DisposableEffect(favoriteViewModel, modelId) {
        favoriteViewModel.open(modelId)
        onDispose { favoriteViewModel.close(modelId) }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteState by favoriteViewModel.uiState.collectAsStateWithLifecycle()
    val favoriteRemoval = LocalFavoriteRemovalTransition.current
    ModelDetailScreen(
        state.takeIf { it.modelId == modelId } ?: ModelDetailUiState(modelId),
        onBack = onBack, onRetry = viewModel::retry,
        favoriteState = favoriteState.takeIf { it.modelId == modelId } ?: FavoriteActionState(modelId),
        onToggleFavorite = {
            favoriteViewModel.toggle(
                canAdd = state.status == ModelDetailStatus.CONTENT,
                onRemovalStarted = { favoriteRemoval?.begin(modelId) },
                onRemovalAborted = { favoriteRemoval?.clear(modelId) },
                onRemoved = onBack,
            )
        },
        onRetryFavorite = favoriteViewModel::retry,
    )
}

@Composable
internal fun ModelDetailScreen(
    uiState: ModelDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    initialIdentity: ModelIdentitySnapshot? = LocalModelSharedTransition.current?.initialIdentity,
    favoriteState: FavoriteActionState = FavoriteActionState(),
    onToggleFavorite: () -> Unit = {},
    onRetryFavorite: () -> Unit = {},
) {
    key(uiState.modelId) {
        ModelDetailBody(uiState, onBack, modifier, onRetry, windowInsets, initialIdentity?.takeIf { it.id == uiState.modelId },
            favoriteState, onToggleFavorite, onRetryFavorite)
    }
}

private enum class DetailTab(val label: Int) {
    OVERVIEW(R.string.detail_tab_overview),
    PROVIDERS(R.string.detail_tab_prices),
    BENCHMARKS(R.string.detail_tab_benchmarks),
}

@Composable
private fun ModelDetailBody(
    uiState: ModelDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier,
    onRetry: () -> Unit,
    windowInsets: WindowInsets,
    initialIdentity: ModelIdentitySnapshot?,
    favoriteState: FavoriteActionState,
    onToggleFavorite: () -> Unit,
    onRetryFavorite: () -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { DetailTab.entries.size })
    val scope = rememberCoroutineScope()
    val activeTab = DetailTab.entries[pagerState.currentPage]
    val sharedListState = rememberLazyListState()
    // 只在公共头部未滚动时匹配身份；已滚动返回仍收缩外层容器，不强行恢复头部。
    val sharedElementsVisible by remember {
        derivedStateOf { sharedListState.firstVisibleItemIndex == 0 && sharedListState.firstVisibleItemScrollOffset == 0 }
    }
    // 列表状态留在标签内容外，避免切换／恢复时惰性布局的首次测量重置锚点。
    val overviewListState = rememberLazyListState()
    val providersListState = rememberLazyListState()
    val benchmarksListState = rememberLazyListState()
    val listStates = listOf(overviewListState, providersListState, benchmarksListState)
    val statusListState = rememberLazyListState()
    val stateHolder = rememberSaveableStateHolder()
    val layoutDirection = LocalLayoutDirection.current
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val containerColor = if (LocalModelSharedTransition.current != null) Color.Transparent else background
    val hasContent = uiState.status == ModelDetailStatus.CONTENT
    val model = uiState.detail.takeIf { hasContent }
    val favoriteDescription = stringResource(when {
        favoriteState.isSaving -> R.string.favorite_saving
        favoriteState.isFavorite -> R.string.favorite_saved
        else -> R.string.favorite_not_saved
    })
    var nameBounds by remember { mutableStateOf<Rect?>(null) }
    val collapseProgress by rememberDetailCollapseProgress(sharedListState, nameBounds)
    val sectionScrollConnection = remember(sharedListState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 上滑先滚走公共头部，再滚正文；下滑由正文先消费，到顶后交还外层列表。
                if (available.y >= 0f) return Offset.Zero
                val consumed = sharedListState.dispatchRawDelta(-available.y)
                return Offset(0f, -consumed)
            }
        }
    }
    Scaffold(
        modifier = modifier.testTag("model-detail-screen"),
        containerColor = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = windowInsets,
    ) { padding ->
        Column(Modifier.fillMaxSize().consumeWindowInsets(padding)) {
            DetailHeaderBar(onBack, padding.calculateTopPadding(), model?.name, { if (hasContent) collapseProgress else 0f },
                Modifier.padding(start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection))) {
                IconToggleButton(
                    checked = favoriteState.isFavorite,
                    onCheckedChange = { onToggleFavorite() },
                    enabled = favoriteState.isLoaded && !favoriteState.isSaving && (hasContent || favoriteState.isFavorite),
                    modifier = Modifier.testTag("detail-favorite").semantics {
                        stateDescription = favoriteDescription
                    },
                ) {
                    Icon(painterResource(if (favoriteState.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                        stringResource(if (favoriteState.isFavorite) R.string.favorite_remove else R.string.favorite_add))
                }
            }
            if (favoriteState.hasReadError || favoriteState.hasWriteError) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(stringResource(if (favoriteState.hasReadError) R.string.favorite_read_error else R.string.favorite_write_error),
                        color = MaterialTheme.colorScheme.error)
                    if (favoriteState.hasReadError) TextButton(onClick = onRetryFavorite) { Text(stringResource(R.string.retry)) }
                }
            }
            if (model != null) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val viewportHeight = maxHeight
                    LazyColumn(
                        state = sharedListState,
                        modifier = Modifier.fillMaxSize().testTag("model-detail-scroll"),
                        contentPadding = PaddingValues(
                            start = padding.calculateStartPadding(layoutDirection),
                            end = padding.calculateEndPadding(layoutDirection),
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "header") {
                            ModelDetailHeader(model, { collapseProgress }, onNameBoundsChanged = { nameBounds = it },
                                sharedElementsEnabled = sharedElementsVisible)
                        }
                        item(key = "tabs") {
                            DetailTabs(activeTab, onSelect = { selected ->
                                scope.launch { pagerState.animateScrollToPage(selected.ordinal) }
                            })
                        }
                        item(key = "sections") {
                            // 只有标签正文参与横向分页；有界视口让正文继续使用惰性列表。
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().height(viewportHeight)
                                    .testTag("model-detail-pager").nestedScroll(sectionScrollConnection),
                                key = { DetailTab.entries[it].name },
                            ) { page ->
                                val tab = DetailTab.entries[page]
                                stateHolder.SaveableStateProvider(tab.name) {
                                    LazyColumn(
                                        state = listStates[page],
                                        modifier = Modifier.fillMaxSize().then(
                                            if (tab == activeTab) Modifier.testTag("model-detail-content")
                                            else Modifier.clearAndSetSemantics {},
                                        ),
                                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
                                        verticalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        when (tab) {
                                            DetailTab.OVERVIEW -> overviewItems(model)
                                            DetailTab.PROVIDERS -> providerItems(model)
                                            DetailTab.BENCHMARKS -> benchmarkItems(model)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                stateHolder.SaveableStateProvider("status") {
                    LazyColumn(
                        state = statusListState,
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("model-detail-content"),
                        // 返回栏独立占据顶部安全区，列表视口从其下方开始，滚动定位不会被固定栏遮挡。
                        contentPadding = PaddingValues(
                            start = padding.calculateStartPadding(layoutDirection),
                            end = padding.calculateEndPadding(layoutDirection),
                            top = if (uiState.status == ModelDetailStatus.LOADING && initialIdentity != null) 0.dp else 16.dp,
                            bottom = padding.calculateBottomPadding() + 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (uiState.status == ModelDetailStatus.LOADING && initialIdentity != null) {
                            item(key = "header") {
                                ModelDetailHeader(null, { 0f }, onNameBoundsChanged = {}, initialIdentity = initialIdentity)
                            }
                        }
                        when (uiState.status) {
                            ModelDetailStatus.LOADING -> item(key = "loading") {
                                DetailSection(stringResource(R.string.detail_loading)) { CircularProgressIndicator() }
                            }
                            ModelDetailStatus.ERROR -> item(key = "error") {
                                DetailSection(stringResource(R.string.model_list_error_title)) {
                                    Text(stringResource(R.string.detail_read_error))
                                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                                }
                            }
                            ModelDetailStatus.NOT_FOUND -> item(key = "missing") {
                                DetailSection(stringResource(if (favoriteState.isFavorite) R.string.favorite_unavailable else R.string.detail_missing_title)) {
                                    favoriteState.identity?.let { FavoriteHistoricalIdentity(it, showModelId = false) }
                                    Text(stringResource(R.string.detail_missing))
                                    Button(onClick = onBack) { Text(stringResource(R.string.navigate_back)) }
                                }
                            }
                            ModelDetailStatus.CONTENT -> Unit
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDetailCollapseProgress(listState: LazyListState, nameBounds: Rect?): State<Float> {
    val approachDistance = with(LocalDensity.current) { 24.dp.toPx() }
    return remember(listState, nameBounds, approachDistance) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f else nameBounds?.let { bounds ->
                // 标题接近列表顶边时开始交接，完全滚出后收起；不受描述／参数卡高度影响。
                val progress = ((listState.firstVisibleItemScrollOffset - bounds.top + approachDistance) /
                    (bounds.height + approachDistance)).coerceIn(0f, 1f)
                FastOutSlowInEasing.transform(progress)
            } ?: 0f
        }
    }
}

@Composable
private fun DetailTabs(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    val minimumTabWidth = 64.dp * LocalDensity.current.fontScale
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("model-detail-tabs")) {
        val scrollable = maxWidth < minimumTabWidth * DetailTab.entries.size
        val tabs: @Composable () -> Unit = {
            DetailTab.entries.forEach { tab ->
                Tab(
                    selected = selected == tab, onClick = { onSelect(tab) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("detail-tab:${tab.name}"),
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(
                        stringResource(tab.label),
                        if (scrollable) Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                        else Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = if (scrollable) TextAlign.Start else TextAlign.Center,
                    )
                }
            }
        }
        if (!scrollable) {
            PrimaryTabRow(selected.ordinal, containerColor = background, tabs = tabs)
        } else {
            PrimaryScrollableTabRow(selected.ordinal, containerColor = background,
                edgePadding = 0.dp, minTabWidth = minimumTabWidth, tabs = tabs)
        }
    }
}

private fun LazyListScope.providerItems(model: ModelDetail) {
    if (model.providers.all { it.offerings.isEmpty() }) item(key = "providers") {
        DetailSection(stringResource(R.string.detail_providers)) {
            Text(stringResource(R.string.detail_no_providers))
        }
    }
    if (model.providers.any { it.offerings.isNotEmpty() }) item(key = "price-unit") {
        Text(stringResource(R.string.model_list_price_unit), Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    model.providers.forEach { provider ->
        provider.offerings.forEach { offering ->
            item(key = "offering:${provider.id.length}:${provider.id}${offering.id}") {
                ModelServiceCard(offering, provider.id, provider.name,
                    Modifier.testTag("model-service:${provider.id.length}:${provider.id}${offering.id}"))
            }
        }
    }
}

private fun LazyListScope.benchmarkItems(model: ModelDetail) {
    item(key = "benchmark-notice") {
        DetailSection(stringResource(R.string.detail_tab_benchmarks)) {
            Text(stringResource(R.string.detail_benchmarks_notice), style = MaterialTheme.typography.bodySmall)
            if (model.benchmarks?.any { it.sourceType == "test" } == true) {
                Text(stringResource(R.string.detail_benchmark_test_notice), style = MaterialTheme.typography.bodySmall)
            }
            if (model.benchmarks == null) Text(stringResource(R.string.detail_benchmarks_missing))
            else if (model.benchmarks.isEmpty()) Text(stringResource(R.string.detail_benchmarks_empty))
        }
    }
    // 评测没有稳定 ID，保留来源顺序及同名、不同配置的所有记录。
    model.benchmarks?.forEachIndexed { index, benchmark ->
        item(key = "benchmark:$index") { BenchmarkSection(benchmark) }
    }
}

@Composable
private fun BenchmarkSection(benchmark: ModelBenchmark) {
    DetailSection(benchmark.name.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data)) {
        DetailField(stringResource(R.string.detail_benchmark_score), benchmark.score.toString())
        DetailField(stringResource(R.string.detail_benchmark_metric), benchmark.metric)
        benchmark.unit?.let { DetailField(stringResource(R.string.detail_benchmark_unit), it) }
        benchmark.higherIsBetter?.let {
            DetailField(stringResource(R.string.detail_benchmark_direction), stringResource(if (it) R.string.detail_benchmark_higher else R.string.detail_benchmark_lower))
        }
        benchmark.reasoningEffort?.let { DetailField(stringResource(R.string.detail_benchmark_reasoning), it) }
        benchmark.toolsEnabled?.let {
            DetailField(stringResource(R.string.detail_benchmark_tools), stringResource(if (it) R.string.detail_benchmark_tools_enabled else R.string.detail_benchmark_tools_disabled))
        }
        val source = benchmark.source?.takeIf(String::isNotBlank)
        // source 也可能是出处名称而非网址；普通文字保留原文，不伪造地址。
        if (source != null && isSafeDetailUrl(source)) {
            DetailLink(stringResource(R.string.detail_benchmark_source), source)
        } else {
            DetailField(stringResource(R.string.detail_benchmark_source), source)
        }
        DetailField(stringResource(R.string.detail_benchmark_date), benchmark.date)
        DetailField(stringResource(R.string.detail_benchmark_version), benchmark.version)
        DetailField(stringResource(R.string.detail_benchmark_variant), benchmark.variant)
        DetailField(stringResource(R.string.detail_benchmark_dataset), benchmark.dataset)
        DetailField(stringResource(R.string.detail_benchmark_harness), benchmark.harness)
    }
}

@Composable
private fun DetailField(label: String, value: String?) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf(String::isNotBlank) ?: stringResource(R.string.no_data), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Preview(name = "Model detail · Light", showBackground = true, widthDp = 400)
@Preview(name = "Model detail · Dark", showBackground = true, widthDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Model detail · Narrow", showBackground = true, widthDp = 320)
@Preview(name = "Model detail · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Preview(name = "Model detail · Dark large font", showBackground = true, widthDp = 320, fontScale = 2f,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelDetailPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        ModelDetailScreen(
            ModelDetailUiState("deepseek/example", ModelDetailStatus.CONTENT, ModelDetail(
                id = "deepseek/example", name = "DeepSeek V4.1 Flash 1017", fetchedAtEpochMillis = 0,
                metadata = ModelDetailMetadata(
                    description = "用于预览的模型描述，展示文本与图片输入、长模型名称及完整参数的排版。",
                    releaseDate = "2026-10-17", contextLimitTokens = 1_000_000, outputLimitTokens = 384_000,
                    inputModalities = listOf("text", "image"), outputModalities = listOf("text"),
                    openWeights = true, reasoning = true, toolCall = true,
                ),
                license = "MIT",
            )), onBack = {},
        )
    }
}
