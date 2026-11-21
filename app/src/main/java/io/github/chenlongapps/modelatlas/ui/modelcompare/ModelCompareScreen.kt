package io.github.chenlongapps.modelatlas.ui.modelcompare

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
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
import io.github.chenlongapps.modelatlas.ui.modellist.LabLogo
import io.github.chenlongapps.modelatlas.ui.modellist.labDisplayName
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

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
        onRetry = viewModel::retry, onRetryCatalog = viewModel::retryCatalog)
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
) {
    var pickerSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            pickerSlot = null
            focusManager.clearFocus()
        }
    }
    // 异步恢复期间使用另一份列表状态，避免短的加载内容夹回已保存的正文位置。
    val contentState = rememberLazyListState()
    val placeholderState = rememberLazyListState()
    var expandedDetails by rememberSaveable(uiState.first.modelId, uiState.second.modelId, stateSaver = ExpandedKeysSaver) {
        mutableStateOf(emptySet<String>())
    }
    var expandedRecords by rememberSaveable(uiState.first.modelId, uiState.second.modelId, stateSaver = ExpandedKeysSaver) {
        mutableStateOf(emptySet<String>())
    }
    var onlyDifferences by rememberSaveable { mutableStateOf(false) }
    val onToggleDetail: (String) -> Unit = { key ->
        expandedDetails = if (key in expandedDetails) expandedDetails - key else expandedDetails + key
    }
    val onToggleRecord: (String) -> Unit = { key ->
        expandedRecords = if (key in expandedRecords) expandedRecords - key else expandedRecords + key
    }
    val first = uiState.first.detail
    val second = uiState.second.detail
    val firstBenchmarks = first?.benchmarks
    val secondBenchmarks = second?.benchmarks
    val (shared, singleSide) = remember(firstBenchmarks, secondBenchmarks) {
        compareBenchmarkGroups(firstBenchmarks, secondBenchmarks).partition(CompareBenchmarkGroup::shared)
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
    val fontScale = LocalDensity.current.fontScale
    val windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    Scaffold(
        modifier = modifier.testTag("compare-screen").drawWithContent {
            drawContent()
            // 与目录一致：内容可滚入状态栏，主题渐变保护系统图标，不占空间或拦截触摸。
            val height = windowInsets.getTop(this) * 1.2f
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
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cardWidth = maxWidth - start - end
            val valueWidth = cardWidth - CompareCardPadding * 2
            val layout = when {
                valueWidth >= 600.dp * fontScale -> CompareLayout.Table
                valueWidth >= 240.dp * fontScale -> CompareLayout.Pair
                else -> CompareLayout.Stacked
            }
            LazyColumn(
                state = if (uiState.isReady) contentState else placeholderState,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("compare-list"),
                contentPadding = PaddingValues(
                    start = start,
                    end = end,
                    top = padding.calculateTopPadding() + 20.dp,
                    bottom = padding.calculateBottomPadding() + bottomOverlayPadding + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("title", contentType = "title") {
                    Text(stringResource(R.string.compare_title), style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
                }
                item("selection", contentType = "selection") {
                    CompareSelection(uiState, cardWidth, fontScale, onChoose = { pickerSlot = it }, onRemove, onRetry)
                }
                if (uiState.catalogStatus == CompareReadStatus.ERROR) {
                    item("catalog-error", contentType = "message") {
                        CompareMessage(stringResource(R.string.compare_catalog_error), onRetryCatalog)
                    }
                } else if (uiState.catalogStatus == CompareReadStatus.LOADING && uiState.first.modelId != null && uiState.second.modelId != null) {
                    item("catalog-loading", contentType = "message") {
                        CompareMessage(stringResource(R.string.compare_loading), loading = true)
                    }
                }
                if (uiState.isReady && specs != null) {
                    item("specs", contentType = "section") {
                        CompareKeySpecs(specs, modelNames, layout, onlyDifferences) { onlyDifferences = it }
                    }
                    item("prices", contentType = "section") {
                        ComparePrices(prices, modelNames, layout, "price-details" in expandedDetails, onToggleDetail)
                    }
                    compareBenchmarkItems(shared, singleSide, modelNames, layout, expandedDetails, onToggleDetail,
                        isRecordExpanded = { it in expandedRecords }, onToggleRecord = onToggleRecord)
                    compareMoreItems(models, prices, modelNames, layout, expandedDetails, onToggleDetail)
                } else {
                    val text = when {
                        uiState.first.modelId != null && uiState.second.modelId != null -> null
                        uiState.first.modelId != null || uiState.second.modelId != null -> R.string.compare_one_selected
                        else -> R.string.compare_empty
                    }
                    if (text != null) item("guidance", contentType = "message") {
                        CompareMessage(stringResource(text))
                    }
                }
            }
        }
    }
    pickerSlot?.takeIf { isVisible }?.let { index ->
        ModelComparePicker(
            uiState, excludedId = uiState.slot(1 - index).modelId,
            onSelect = { onSelect(index, it); pickerSlot = null },
            onDismiss = { pickerSlot = null }, onRetry = onRetryCatalog,
        )
    }
}

private val ExpandedKeysSaver = Saver<Set<String>, List<String>>(
    save = { ArrayList(it) }, restore = { it.toSet() },
)

@Composable
private fun CompareSelection(
    state: ModelCompareUiState, width: Dp, fontScale: Float,
    onChoose: (Int) -> Unit, onRemove: (Int) -> Unit, onRetry: (Int) -> Unit,
) {
    run {
        val density = LocalDensity.current
        val stacked = width < 280.dp * fontScale
        val slots = listOf(state.first, state.second)
        val summaries = slots.map { slot -> state.models.firstOrNull { it.id == slot.modelId } }
        val names = slots.mapIndexed { index, slot ->
            slot.detail?.name ?: summaries[index]?.name
                ?: slot.modelId?.let { stringResource(R.string.compare_unknown_model, index + 1) }
        }
        val labs = slots.mapIndexed { index, slot -> slot.detail?.lab ?: summaries[index]?.lab }
        val titles = names.mapIndexed { index, name -> name ?: stringResource(R.string.compare_select, index + 1) }
        val labNames = labs.map { it?.let(::labDisplayName) ?: stringResource(R.string.no_data) }
        val textMeasurer = rememberTextMeasurer()
        // 以较窄一列的实际文字宽度测量，首帧就同步两侧字段高度，不通过布局回调反复补高。
        val textWidth = with(density) {
            ((width.roundToPx() - 12.dp.roundToPx()) / 2 - 2 * 10.dp.roundToPx()).coerceAtLeast(1)
        }
        fun sharedTextHeight(texts: List<String>, style: TextStyle): Dp = if (stacked) 0.dp else with(density) {
            texts.maxOf { textMeasurer.measure(it, style = style, constraints = Constraints(maxWidth = textWidth)).size.height }.toDp()
        }
        val nameHeight = sharedTextHeight(titles, MaterialTheme.typography.titleMedium)
        val labHeight = sharedTextHeight(labNames, MaterialTheme.typography.bodySmall)
        val card: @Composable (Int, Modifier) -> Unit = { index, modifier ->
            CompareSelectionCard(slots[index], names[index], labs[index], labNames[index], index,
                { onChoose(index) }, { onRemove(index) }, { onRetry(index) }, modifier, nameHeight, labHeight)
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                card(0, Modifier.fillMaxWidth())
                card(1, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                card(0, Modifier.weight(1f).fillMaxHeight())
                card(1, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CompareSelectionCard(
    state: CompareModelState, name: String?, lab: String?, labName: String, index: Int,
    onChoose: () -> Unit, onRemove: () -> Unit, onRetry: () -> Unit, modifier: Modifier,
    nameMinHeight: Dp, labMinHeight: Dp,
) {
    val label = if (name == null) stringResource(R.string.compare_select, index + 1)
        else stringResource(R.string.compare_replace, name)
    Surface(onClick = onChoose, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("compare-slot-$index"),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.modelId == null) {
                    Icon(painterResource(R.drawable.ic_compare), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                } else {
                    LabLogo(lab, size = 28.dp)
                    Text(stringResource(R.string.compare_change),
                        modifier = Modifier.weight(1f).padding(start = 4.dp).testTag("compare-change-$index").semantics {
                            contentDescription = label
                        },
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onRemove, modifier = Modifier.testTag("compare-remove-$index")) {
                        Icon(painterResource(R.drawable.ic_close), stringResource(R.string.compare_remove,
                            name ?: stringResource(R.string.compare_unknown_model, index + 1)),
                            Modifier.size(20.dp))
                    }
                }
            }
            Text(name ?: stringResource(R.string.compare_select, index + 1), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.heightIn(min = nameMinHeight).testTag("compare-name-$index"))
            if (name != null) {
                Text(labName, modifier = Modifier.heightIn(min = labMinHeight).testTag("compare-lab-$index"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.modelId != null) {
                when (state.status) {
                    CompareReadStatus.LOADING -> Text(stringResource(R.string.compare_loading), style = MaterialTheme.typography.bodySmall)
                    CompareReadStatus.NOT_FOUND -> Text(stringResource(R.string.compare_missing), style = MaterialTheme.typography.bodySmall)
                    CompareReadStatus.ERROR -> {
                        Text(stringResource(R.string.detail_read_error), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    CompareReadStatus.CONTENT -> Unit
                }
            }
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
                reasoningEffort = "High", benchmarkId = "swe-bench-pro-v2"))),
        ModelDetail("moonshotai/kimi-k3.1-pro", "Kimi K3.1 Pro", ModelDetailMetadata(releaseDate = "2026", contextLimitTokens = 400_000,
            outputLimitTokens = 128_000, inputModalities = listOf("text", "image", "audio"), outputModalities = listOf("text"),
            reasoning = true, structuredOutput = true, openWeights = false), fetchedAtEpochMillis = 0,
            benchmarks = listOf(ModelBenchmark("SWE-bench Pro", null, scoreText = "74.5 ± 1.3", metric = "pass@1",
                version = "2", unit = "%", benchmarkId = "swe-bench-pro-v2"))),
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
