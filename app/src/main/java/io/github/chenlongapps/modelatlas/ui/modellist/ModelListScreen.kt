package io.github.chenlongapps.modelatlas.ui.modellist

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.model.LocalModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentitySnapshot
import io.github.chenlongapps.modelatlas.ui.favorites.FavoriteHistoricalIdentity
import io.github.chenlongapps.modelatlas.ui.favorites.FavoritesUiState
import io.github.chenlongapps.modelatlas.ui.favorites.FavoritesViewModel
import io.github.chenlongapps.modelatlas.ui.favorites.LocalFavoriteRemovalTransition
import io.github.chenlongapps.modelatlas.ui.favorites.RetainedFavorite
import kotlinx.coroutines.delay

@Composable
fun ModelListRoute(
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
    viewModel: ModelListViewModel = hiltViewModel(),
    listState: LazyListState = rememberLazyListState(),
    onModelClick: (String) -> Unit = {},
    favoritesViewModel: FavoritesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoritesState by favoritesViewModel.uiState.collectAsStateWithLifecycle()
    ModelListScreen(
        uiState = uiState,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onApplyUpdate = viewModel::applyUpdate,
        onDismissNotice = viewModel::clearMessage,
        onQueryChange = viewModel::setQuery,
        modifier = modifier,
        bottomOverlayPadding = bottomOverlayPadding,
        listState = listState,
        onModelClick = onModelClick,
        favoritesState = favoritesState,
        onRetryFavorites = favoritesViewModel::retry,
    )
}

@Composable
internal fun ModelListScreen(
    uiState: ModelListUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    onRefresh: () -> Unit = {},
    onApplyUpdate: () -> Unit = {},
    onDismissNotice: () -> Unit = {},
    bottomOverlayPadding: Dp = 0.dp,
    listState: LazyListState = rememberLazyListState(),
    onQueryChange: (ModelListQuery) -> Unit = {},
    onModelClick: (String) -> Unit = {},
    favoritesState: FavoritesUiState = FavoritesUiState(isLoading = false),
    onRetryFavorites: () -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val showSkeletons = uiState.isLoading && uiState.models.isEmpty()
    val shimmerProgress = rememberModelSkeletonShimmer(showSkeletons)
    // 展开状态提到列表层，避免滚动回收卡片后丢失，同时便于状态恢复。
    var expandedPriceModelId by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val isVisible = LocalModelListVisible.current
    val sharedTransition = LocalModelSharedTransition.current
    val removal = LocalFavoriteRemovalTransition.current
    val favorites = if (favoritesState.hasError) emptyList() else favoritesState.favorites
    val rawEntries = catalogEntries(uiState.models, favorites, uiState.query).let { entries ->
        // 目录读取失败不展示过期目录，但仍允许访问已独立保存的下架收藏。
        if (uiState.error != null) entries.filter { it.model == null } else entries
    }
    val currentEntries by rememberUpdatedState(rawEntries)
    DisposableEffect(removal) {
        removal?.source = { id ->
            val index = currentEntries.indexOfFirst { it.id == id }
            currentEntries.getOrNull(index)?.favorite?.let { RetainedFavorite(it, index) }
        }
        onDispose { removal?.source = null }
    }
    val retained = removal?.retained
    val missingRetained = retained != null && favorites.none { it.identity.id == retained.favorite.identity.id }
    val retainedAlpha = remember(retained?.favorite?.identity?.id) { Animatable(1f) }
    LaunchedEffect(retained, removal?.awaitingReturn, missingRetained, favoritesState.hasError) {
        if (retained != null && favoritesState.hasError) removal.clear(retained.favorite.identity.id)
        else if (retained != null && missingRetained && !removal.awaitingReturn) {
            if (retained.favorite.model == null) retainedAlpha.animateTo(0f, tween(180))
            removal.clear(retained.favorite.identity.id)
        }
    }
    val entries = rawEntries.toMutableList().apply {
        if (retained != null && missingRetained && retained.favorite.model == null && !favoritesState.hasError &&
            none { it.id == retained.favorite.identity.id }) {
            add(retained.index.coerceIn(0, size), CatalogEntry(retained.favorite.identity.id, null, retained.favorite))
        }
        val pinnedIndex = indexOfFirst { it.id == removal?.pinnedId }
        if (pinnedIndex >= 0 && removal != null) {
            val entry = removeAt(pinnedIndex)
            add(removal.pinnedIndex.coerceIn(0, size), entry)
        }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            focusManager.clearFocus()
            showFilters = false
        }
    }
    // 条件变化时离开旧结果位置；已可见的搜索框不回滚，避免将焦点推到键盘后。
    // 目录刷新、提醒到达和状态恢复不触发回顶。
    var previousQuery by remember { mutableStateOf(uiState.query) }
    LaunchedEffect(uiState.query) {
        if (previousQuery != uiState.query) {
            previousQuery = uiState.query
            if (listState.firstVisibleItemIndex > 1) listState.scrollToItem(0)
        }
    }
    val pullState = rememberPullToRefreshState()
    val refreshLabel = stringResource(R.string.model_list_refresh)
    val accessibilityManager = LocalAccessibilityManager.current
    val dismissNotice by rememberUpdatedState(onDismissNotice)
    val transientMessage = uiState.message.takeIf {
        uiState.hasCatalogNotice() && !uiState.isRefreshing && uiState.refreshError == null
    }
    // 不放在 LazyColumn 条目内，避免滚动回收／重建提醒时重置计时。
    LaunchedEffect(transientMessage, accessibilityManager) {
        if (transientMessage != null) {
            val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 3_000L,
                containsIcons = true,
                containsText = true,
                containsControls = false,
            ) ?: 3_000L
            delay(timeout)
            dismissNotice()
        }
    }

    Scaffold(
        modifier = modifier
            .imePadding()
            .drawWithContent {
                drawContent()
                // 最后绘制半透明保护层，不占布局空间，也不拦截触摸。
                val protectionHeight = windowInsets.getTop(this) * 1.2f
                if (protectionHeight > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.9f),
                                backgroundColor.copy(alpha = 0.6f),
                                Color.Transparent,
                            ),
                            endY = protectionHeight,
                        ),
                        size = Size(size.width, protectionHeight),
                    )
                }
            },
        containerColor = backgroundColor,
        contentWindowInsets = windowInsets,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { if (!uiState.isLoading) onRefresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding()),
                )
            },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("model-list")
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction(refreshLabel) {
                            if (uiState.isLoading || uiState.isRefreshing) false else {
                                onRefresh()
                                true
                            }
                        })
                    }
                    .consumeWindowInsets(innerPadding),
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + bottomOverlayPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "catalog-header") {
                    Column {
                        CatalogHeader(uiState = uiState)
                        AnimatedCatalogStatusNotice(
                            notice = uiState.catalogNotice(),
                            onApplyUpdate = onApplyUpdate,
                            onRefresh = onRefresh,
                            onDismiss = onDismissNotice,
                        )
                    }
                }
                if (showSkeletons || (!uiState.isLoading && (uiState.error == null || favorites.isNotEmpty()))) {
                    item(key = "catalog-query", contentType = "catalog-query") {
                        ModelCatalogControls(
                            query = uiState.query,
                            models = uiState.models,
                            onQueryChange = onQueryChange,
                            onShowFilters = { showFilters = true },
                            skeletonProgress = shimmerProgress.takeIf { showSkeletons },
                        )
                    }
                }
                if (favoritesState.hasError && !showSkeletons) {
                    item(key = "favorites-error") {
                        CatalogStateCard(title = stringResource(R.string.nav_favorites),
                            message = stringResource(R.string.favorites_read_error), onRetry = onRetryFavorites)
                    }
                }
                if (showSkeletons || entries.isNotEmpty()) {
                    item(key = "sort-order", contentType = "sort-order") {
                        Text(
                            text = stringResource(R.string.model_list_order),
                            modifier = Modifier.padding(horizontal = 4.dp)
                                .modelSkeletonPlaceholder(
                                    shimmerProgress.takeIf { showSkeletons },
                                    RoundedCornerShape(4.dp),
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    showSkeletons -> items(count = 6, key = { "skeleton:$it" }) { index ->
                        ModelCardSkeleton(shimmerProgress = shimmerProgress, index = index)
                    }
                    uiState.error != null -> item(key = "error") {
                        val message = uiState.error.messageResource()
                        CatalogStateCard(
                            title = stringResource(R.string.model_list_error_title),
                            message = stringResource(message),
                            onRetry = onRetry,
                        )
                    }
                    uiState.models.isEmpty() && entries.isEmpty() -> item(key = "empty") {
                        CatalogStateCard(
                            title = stringResource(R.string.model_list_empty_title),
                            message = stringResource(R.string.model_list_empty),
                        )
                    }
                    entries.isEmpty() -> item(key = "no-results") {
                        CatalogStateCard(
                            title = stringResource(R.string.model_query_empty_title),
                            message = stringResource(R.string.model_query_empty),
                            onClearQuery = { onQueryChange(ModelListQuery()) },
                        )
                    }
                }
                if (!showSkeletons) items(items = entries, key = { "model:${it.id}" }) { entry ->
                    val model = entry.model
                    val isRetained = retained?.favorite?.identity?.id == entry.id && missingRetained
                    Box(Modifier.animateItem(placementSpec = tween(220))
                        .graphicsLayer { alpha = if (isRetained && model == null) retainedAlpha.value else 1f }
                        .then(if (isRetained && model == null) Modifier.clearAndSetSemantics {} else Modifier)) {
                    if (model != null) ModelCard(
                        model = model,
                        onOpenDetail = {
                            removal?.pin(model.id, entries.indexOfFirst { it.id == model.id })
                            sharedTransition?.captureIdentity(ModelIdentitySnapshot(
                                model.id, model.name, model.lab, model.openWeights, model.license,
                            ))
                            onModelClick(model.id)
                        },
                        priceDetailsExpanded = expandedPriceModelId == model.id,
                        onTogglePriceDetails = {
                            expandedPriceModelId =
                                if (expandedPriceModelId == model.id) null else model.id
                        },
                        isFavorite = entry.favorite != null,
                    )
                    else entry.favorite?.let { favorite ->
                        Surface(Modifier.fillMaxWidth().testTag("favorite-unavailable:${entry.id}")
                            .clickable(enabled = !isRetained, role = Role.Button, onClickLabel = stringResource(R.string.detail_open)) {
                                removal?.pin(entry.id, entries.indexOfFirst { it.id == entry.id })
                                onModelClick(entry.id)
                            }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FavoriteHistoricalIdentity(favorite.identity)
                                Text(stringResource(R.string.favorite_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    if (showFilters && isVisible && !uiState.isLoading && (uiState.error == null || favorites.isNotEmpty())) {
        ModelFilterSheet(
            models = uiState.models,
            query = uiState.query,
            onQueryChange = onQueryChange,
            onDismiss = { showFilters = false },
        )
    }
}

internal fun ModelListError.messageResource() = when (this) {
    ModelListError.NETWORK -> R.string.model_list_network_error
    ModelListError.SERVER -> R.string.model_list_server_error
    ModelListError.INVALID_DATA -> R.string.model_list_invalid_data
    ModelListError.RATE_LIMITED -> R.string.model_list_rate_limited
    ModelListError.STORAGE -> R.string.model_list_storage_error
}

@Composable
private fun CatalogHeader(uiState: ModelListUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog-header")
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = if (!uiState.isLoading && uiState.error == null) {
                if (uiState.query.isActive) {
                    stringResource(R.string.model_query_count, uiState.visibleModels.size, uiState.models.size)
                } else {
                    stringResource(R.string.model_list_count, uiState.models.size)
                }
            } else {
                stringResource(R.string.model_list_title)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogStateCard(
    title: String,
    message: String,
    onRetry: (() -> Unit)? = null,
    onClearQuery: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_catalog), contentDescription = null, modifier = Modifier.size(32.dp))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRetry != null) {
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            if (onClearQuery != null) {
                Button(onClick = onClearQuery) { Text(stringResource(R.string.model_query_reset)) }
            }
        }
    }
}

@Preview(name = "Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", showBackground = true, widthDp = 360, fontScale = 2f)
@Composable
private fun ModelListScreenPreview() {
    ModelAtlasTheme {
        ModelListScreen(
            uiState = ModelListUiState(
                models = listOf(
                    ModelSummary(
                        id = "deepseek/deepseek-v4.1-flash-1017",
                        name = "DeepSeek V4.1 Flash 1017",
                        releaseDate = "2026-10-17",
                        contextLimitTokens = 1_000_000,
                        outputLimitTokens = 384_000,
                        inputModalities = listOf("text", "image"),
                        outputModalities = listOf("text"),
                        openWeights = true,
                        license = "MIT",
                        price = ModelPriceSummary(
                            providerId = "deepseek",
                            providerName = "DeepSeek",
                            isOfficial = true,
                            input = 0.60,
                            output = 0.15,
                            cacheRead = 0.003,
                            tiers = listOf(
                                ModelPriceTierSummary(
                                    conditionType = "context",
                                    conditionSizeTokens = 272_000,
                                    input = 0.3,
                                    output = 1.2,
                                ),
                            ),
                        ),
                    ),
                    ModelSummary(
                        id = "meta/muse-spark-open",
                        name = "Muse Spark Open",
                        releaseDate = "2026-10-15",
                        contextLimitTokens = 1_048_576,
                        outputLimitTokens = 131_072,
                        inputModalities = listOf("text", "image", "video", "audio"),
                        outputModalities = listOf("text"),
                        openWeights = true,
                        license = "Apache-2.0",
                        price = ModelPriceSummary(
                            providerId = "openrouter",
                            providerName = "OpenRouter",
                            isOfficial = false,
                            input = 0.1,
                            output = 0.2,
                        ),
                    ),
                    ModelSummary(
                        id = "nvidia/nemotron-3.5-super-vl-120b-a12b",
                        name = "Nemotron 3.5 Super VL 120B A12B",
                        releaseDate = "2026-10-15",
                        contextLimitTokens = 1_048_576,
                        outputLimitTokens = 8_192,
                        inputModalities = listOf("text", "image", "video"),
                        outputModalities = listOf("text"),
                        openWeights = false,
                    ),
                    // 没有随包 logo 素材的 LAB 显示兜底图。
                    ModelSummary(
                        id = "acme/atlas-mini",
                        name = "Atlas Mini",
                        releaseDate = "2026-10-01",
                        contextLimitTokens = 128_000,
                        outputLimitTokens = 4_096,
                        inputModalities = listOf("text"),
                        outputModalities = listOf("text"),
                        openWeights = true,
                        license = "Apache-2.0",
                    ),
                ),
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "Loading · Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Loading · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Loading · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun ModelListLoadingPreview() {
    ModelAtlasTheme { ModelListScreen(ModelListUiState()) }
}

@Preview(name = "Error", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelListErrorPreview() {
    ModelAtlasTheme {
        ModelListScreen(ModelListUiState(isLoading = false, error = ModelListError.NETWORK))
    }
}

@Preview(name = "Empty", showBackground = true)
@Composable
private fun ModelListEmptyPreview() {
    ModelAtlasTheme { ModelListScreen(ModelListUiState(isLoading = false)) }
}

@Preview(name = "No results · Light", showBackground = true)
@Preview(name = "No results · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelListNoResultsPreview() {
    ModelAtlasTheme {
        ModelListScreen(ModelListUiState(
            models = listOf(ModelSummary("lab/atlas", "Atlas")),
            isLoading = false,
            query = ModelListQuery(search = "vision"),
        ))
    }
}
