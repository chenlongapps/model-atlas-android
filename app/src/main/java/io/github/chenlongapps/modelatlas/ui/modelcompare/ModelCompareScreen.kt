package io.github.chenlongapps.modelatlas.ui.modelcompare

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

private enum class CompareInfo { Prices, Benchmarks }

private const val SelectionKey = "selection"
private val PinnedBottomPadding = 6.dp

private fun LazyListLayoutInfo.selectionCollapseFraction(travelPx: Float): Float {
    // 吸顶条目也出现在 visibleItemsInfo 中，吸顶时 offset 等于视口顶边。
    val selection = visibleItemsInfo.firstOrNull { it.key == SelectionKey } ?: return 0f
    return (1f - (selection.offset - viewportStartOffset) / travelPx).coerceIn(0f, 1f)
}

@Composable
internal fun ModelCompareRoute(bottomOverlayPadding: Dp, isVisible: Boolean, viewModel: ModelCompareViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(viewModel, lifecycle, isVisible) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setActive(isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        viewModel.setActive(isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {
            lifecycle.removeObserver(observer)
            viewModel.setActive(false)
        }
    }
    ModelCompareScreen(state, bottomOverlayPadding = bottomOverlayPadding, isVisible = isVisible,
        onSelect = viewModel::select, onRemove = viewModel::remove,
        onRetry = viewModel::retry, onRetryCatalog = viewModel::retryCatalog,
        onRetrySelectionLoad = viewModel::retrySelectionLoad, onRetrySelectionSave = viewModel::retrySelectionSave)
}

@Composable
internal fun ModelCompareScreen(
    uiState: ModelCompareUiState,
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
    isVisible: Boolean = true,
    onSelect: (Int, String) -> Unit = { _, _ -> },
    onRemove: (Int) -> Unit = {},
    onRetry: (Int) -> Unit = {},
    onRetryCatalog: () -> Unit = {},
    onRetrySelectionLoad: () -> Unit = {},
    onRetrySelectionSave: () -> Unit = {},
) {
    var pickerSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var openInfo by remember(uiState.first.modelId, uiState.second.modelId) { mutableStateOf<CompareInfo?>(null) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isVisible, uiState.isReady) {
        if (!isVisible) {
            pickerSlot = null
            focusManager.clearFocus()
        }
        if (!isVisible || !uiState.isReady) openInfo = null
    }
    // 异步恢复期间使用另一份列表状态，避免短的加载内容夹回已保存的正文位置。
    val contentState = rememberLazyListState()
    val placeholderState = rememberLazyListState()
    val selectionSlots = compareSelectionSlots(uiState)
    var onlyDifferences by rememberSaveable { mutableStateOf(false) }
    val first = uiState.first.detail
    val second = uiState.second.detail
    val firstBenchmarks = first?.benchmarks
    val secondBenchmarks = second?.benchmarks
    val benchmarks = remember(firstBenchmarks, secondBenchmarks) {
        compareBenchmarkGroups(firstBenchmarks, secondBenchmarks)
    }
    val models = remember(first, second) { if (first != null && second != null) listOf(first, second) else emptyList() }
    val modelNames = remember(models) { models.map(ModelDetail::name) }
    val specs = remember(first?.metadata, second?.metadata) {
        if (first != null && second != null) CompareSpecs(first.metadata, second.metadata) else null
    }
    val prices = remember(uiState.models, first?.id, second?.id) {
        listOf(uiState.models.firstOrNull { it.id == first?.id }?.price, uiState.models.firstOrNull { it.id == second?.id }?.price)
    }
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val collapseTravelPx = with(density) { 56.dp.toPx() }
    val selectionCollapse = remember(contentState, collapseTravelPx) {
        derivedStateOf { contentState.layoutInfo.selectionCollapseFraction(collapseTravelPx) }
    }
    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    Scaffold(
        modifier = modifier.testTag("compare-screen").drawWithContent {
            drawContent()
            // 非正文状态仍可滚入状态栏，由渐变保护系统图标。
            val height = if (uiState.isReady) 0f else windowInsets.getTop(this) * 1.2f
            if (height > 0f) drawRect(
                Brush.verticalGradient(listOf(background.copy(alpha = 0.9f), background.copy(alpha = 0.6f), Color.Transparent), endY = height),
                size = Size(size.width, height),
            )
        },
        containerColor = background,
        contentWindowInsets = windowInsets,
    ) { padding ->
        val start = padding.calculateStartPadding(direction) + 16.dp
        val end = padding.calculateEndPadding(direction) + 16.dp
        val topInset = padding.calculateTopPadding()
        val bottomPadding = padding.calculateBottomPadding() + bottomOverlayPadding + 16.dp
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cardWidth = with(density) {
                (maxWidth.roundToPx() - start.roundToPx() - end.roundToPx()).coerceAtLeast(0).toDp()
            }
            val selectionLayout = if (uiState.selectionStatus == CompareReadStatus.CONTENT)
                compareSelectionLayout(selectionSlots, cardWidth) else null
            val layout = when {
                selectionLayout?.stacked == true -> CompareLayout.Stacked
                cardWidth >= 600.dp * fontScale.coerceAtLeast(1f) -> CompareLayout.Table
                else -> CompareLayout.Pair
            }
            // 用紧凑态的文字和布局尺寸判断，不让展开或过渡高度改变同一个列表项是否吸顶。
            val readableHeight = maxHeight - topInset - bottomPadding
            val pinHeightLimit = minOf(readableHeight / 2, readableHeight - 144.dp)
            val pinSelection = uiState.isReady && selectionLayout != null &&
                selectionLayout.slotCompactHeight < selectionLayout.slotExpandedHeight &&
                with(density) { selectionLayout.compactHeight.toDp() } + PinnedBottomPadding <= pinHeightLimit
            // stickyHeader 贴到列表视口的顶边，不会避开 contentPadding 中的系统栏。
            val viewportTop = if (uiState.isReady) topInset else 0.dp
            LazyColumn(
                state = if (uiState.isReady) contentState else placeholderState,
                modifier = Modifier.fillMaxSize().padding(top = viewportTop)
                    .consumeWindowInsets(padding).testTag("compare-list"),
                contentPadding = PaddingValues(
                    start = start,
                    end = end,
                    top = topInset - viewportTop + 20.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("title", contentType = "title") {
                    Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
                }
                if (selectionLayout == null) {
                    item("selection-restore", contentType = "message") {
                        if (uiState.selectionStatus == CompareReadStatus.ERROR) {
                            CompareMessage(stringResource(R.string.compare_selection_read_error), onRetrySelectionLoad)
                        } else {
                            CompareMessage(stringResource(R.string.compare_selection_loading), loading = true)
                        }
                    }
                    return@LazyColumn
                }
                val selection: @Composable () -> Unit = {
                    Box(
                        Modifier.fillMaxWidth().background(background)
                            .padding(bottom = if (pinSelection) PinnedBottomPadding else 0.dp),
                    ) {
                        CompareSelection(selectionSlots, selectionLayout,
                            fraction = { if (pinSelection) selectionCollapse.value else 0f },
                            onChoose = { pickerSlot = it }, onRetry = onRetry)
                    }
                }
                if (pinSelection) {
                    stickyHeader(SelectionKey, contentType = SelectionKey) { _ -> selection() }
                } else {
                    item(SelectionKey, contentType = SelectionKey) { selection() }
                }
                if (uiState.selectionSaveFailed) {
                    item("selection-save-error", contentType = "message") {
                        CompareMessage(stringResource(R.string.compare_selection_save_error), onRetrySelectionSave)
                    }
                }
                if (uiState.catalogStatus == CompareReadStatus.ERROR) {
                    item("catalog-error", contentType = "message") {
                        CompareMessage(stringResource(R.string.compare_catalog_error), onRetryCatalog)
                    }
                } else if (uiState.catalogStatus == CompareReadStatus.LOADING && specs != null) {
                    item("catalog-loading", contentType = "message") {
                        CompareMessage(stringResource(R.string.compare_catalog_loading), loading = true)
                    }
                }
                if (uiState.isReady && specs != null) {
                    item("specs", contentType = "section") {
                        CompareKeySpecs(specs, modelNames, layout, onlyDifferences) { onlyDifferences = it }
                    }
                    item("prices", contentType = "section") {
                        ComparePrices(prices, modelNames, layout) { openInfo = CompareInfo.Prices }
                    }
                    compareModelInfoItems(models, modelNames, layout, onlyDifferences)
                    compareBenchmarkItems(benchmarks, modelNames, models.map(ModelDetail::lab)) { openInfo = CompareInfo.Benchmarks }
                }
            }
        }
    }
    pickerSlot?.takeIf { isVisible && uiState.selectionStatus == CompareReadStatus.CONTENT }?.let { index ->
        val slot = selectionSlots[index]
        ModelComparePicker(
            uiState, excludedId = uiState.slot(1 - index).modelId,
            selectedName = slot.name,
            onSelect = {
                if (slot.state.modelId == null && uiState.slot(1 - index).modelId != null) {
                    // 主动补齐一组对比时沿用当前选模位置，不跳回上一组的正文。
                    contentState.requestScrollToItem(
                        placeholderState.firstVisibleItemIndex, placeholderState.firstVisibleItemScrollOffset,
                    )
                }
                onSelect(index, it)
                pickerSlot = null
            },
            onRemove = { onRemove(index); pickerSlot = null },
            onDismiss = { pickerSlot = null }, onRetry = onRetryCatalog,
        )
    }
    if (isVisible && uiState.isReady && uiState.selectionStatus == CompareReadStatus.CONTENT) {
        when (openInfo) {
            CompareInfo.Prices -> if (prices.any { it != null }) {
                CompareInfoDialog(R.string.compare_price_info, onDismiss = { openInfo = null }) {
                    Text(stringResource(R.string.compare_price_conditions))
                }
            }
            CompareInfo.Benchmarks -> if (benchmarks.any { it.referenceOnly }) {
                CompareBenchmarkInfo(benchmarks, modelNames) { openInfo = null }
            }
            null -> Unit
        }
    }
}

@Composable
internal fun CompareMessage(text: String, onRetry: (() -> Unit)? = null, loading: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Preview(name = "Comparison · Light", widthDp = 420, heightDp = 1600, showBackground = true)
@Preview(name = "Comparison · Dark", widthDp = 420, heightDp = 1600, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Comparison · Phone long names", widthDp = 400, heightDp = 1300, showBackground = true)
@Preview(name = "Comparison · Wide", widthDp = 840, heightDp = 1200, showBackground = true)
@Preview(name = "Comparison · Narrow large font", widthDp = 320, heightDp = 1000, fontScale = 2f, showBackground = true)
@Composable
private fun ModelComparePreview() {
    val details = listOf(
        ModelDetail("deepseek/deepseek-v4.1-flash-1017", "DeepSeek V4.1 Flash 1017", ModelDetailMetadata(releaseDate = "2026-10", contextLimitTokens = 1_000_000,
            outputLimitTokens = 131_072, inputModalities = listOf("text", "image"), outputModalities = listOf("text"),
            reasoning = true, toolCall = true, openWeights = true), license = "MIT", fetchedAtEpochMillis = 0,
            benchmarks = listOf(ModelBenchmark("SWE-bench Pro", 78.4, metric = "pass@1", version = "2", unit = "%",
                reasoningEffort = "High", benchmarkId = "swe-bench-pro-v2",
                dataset = "public", harness = "runner", source = "https://example.org/results"))),
        ModelDetail("moonshotai/kimi-k3.1-pro", "Kimi K3.1 Pro", ModelDetailMetadata(releaseDate = "2026", contextLimitTokens = 400_000,
            outputLimitTokens = 128_000, inputModalities = listOf("text", "image", "audio"), outputModalities = listOf("text"),
            reasoning = true, structuredOutput = true, openWeights = false), fetchedAtEpochMillis = 0,
            benchmarks = listOf(ModelBenchmark("SWE-bench Pro", null, scoreText = "74.5 ± 1.3", metric = "pass@1",
                version = "2", unit = "%", reasoningEffort = "Max", benchmarkId = "swe-bench-pro-v2",
                dataset = "public", harness = "runner", source = "https://example.org/results"))),
    )
    ModelAtlasTheme(dynamicColor = false) {
        ModelCompareScreen(ModelCompareUiState(
            models = details.map { ModelSummary(it.id, it.name, price = ModelPriceSummary("example", "Example provider", false, input = 0.003, output = 2.0)) },
            catalogStatus = CompareReadStatus.CONTENT,
            first = CompareModelState(details[0].id, CompareReadStatus.CONTENT, details[0]),
            second = CompareModelState(details[1].id, CompareReadStatus.CONTENT, details[1]),
        ))
    }
}
