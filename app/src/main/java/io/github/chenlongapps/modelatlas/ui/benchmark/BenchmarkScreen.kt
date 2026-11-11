package io.github.chenlongapps.modelatlas.ui.benchmark

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoard
import io.github.chenlongapps.modelatlas.data.model.BenchmarkBoardEntry
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import io.github.chenlongapps.modelatlas.ui.modellist.AnimatedCatalogStatusNotice
import io.github.chenlongapps.modelatlas.ui.modellist.CatalogNotice
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun BenchmarkRoute(
    bottomOverlayPadding: Dp,
    isVisible: Boolean,
    onOpen: (String) -> Unit,
    viewModel: BenchmarkViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // 主分页器始终组合；排行榜打开期间外壳继续传入 true，统一由此管理订阅。
    DisposableEffect(viewModel, lifecycle, isVisible) {
        val observer = LifecycleEventObserver { _, _ ->
            viewModel.setActive(isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        lifecycle.addObserver(observer)
        viewModel.setActive(isVisible && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { lifecycle.removeObserver(observer); viewModel.setActive(false) }
    }
    BenchmarkScreen(state, onOpen, bottomOverlayPadding = bottomOverlayPadding, onRetry = viewModel::retry,
        onRefresh = viewModel::refresh, onDismissNotice = viewModel::clearMessage)
}

@Composable
internal fun BenchmarkLeaderboardRoute(boardId: String, onBack: () -> Unit, viewModel: BenchmarkViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BenchmarkLeaderboardScreen(state, boardId, onBack, onRetry = viewModel::retry)
}

@Composable
internal fun BenchmarkScreen(
    uiState: BenchmarkUiState,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onDismissNotice: () -> Unit = {},
) {
    val contentState = rememberLazyListState()
    val placeholderState = rememberLazyListState()
    val direction = LocalLayoutDirection.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val pullState = rememberPullToRefreshState()
    val refreshLabel = stringResource(R.string.benchmark_refresh)
    val accessibilityManager = LocalAccessibilityManager.current
    val dismissNotice by rememberUpdatedState(onDismissNotice)
    val notice = if (!uiState.isLoading && !uiState.isRefreshing &&
        (uiState.message != null || uiState.refreshError != null)) {
        CatalogNotice(uiState.refreshError, uiState.message)
    } else null
    val transientMessage = notice?.message.takeIf { notice?.refreshError == null }
    // 计时在列表条目外，滚动回收不重置；与模型页使用同一无障碍建议时长。
    LaunchedEffect(transientMessage, accessibilityManager) {
        if (transientMessage != null) {
            val timeout = accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = 3_000L, containsIcons = true, containsText = true, containsControls = false,
            ) ?: 3_000L
            delay(timeout)
            dismissNotice()
        }
    }
    val repeatedNames = remember(uiState.boards) {
        uiState.boards.groupingBy { it.name.trim().lowercase(Locale.ROOT) }.eachCount().filterValues { it > 1 }.keys
    }
    Scaffold(
        modifier = modifier.testTag("benchmark-screen").drawWithContent {
            drawContent()
            val height = insets.getTop(this) * 1.2f
            if (height > 0f) drawRect(
                Brush.verticalGradient(listOf(background.copy(alpha = 0.9f), background.copy(alpha = 0.6f), Color.Transparent), endY = height),
                size = Size(size.width, height),
            )
        },
        containerColor = background, contentWindowInsets = insets,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { if (!uiState.isLoading && !uiState.isRefreshing) onRefresh() },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullState, isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding())
                        .testTag("benchmark-refresh-indicator"),
                )
            },
        ) {
            LazyColumn(
                state = if (uiState.isLoading || uiState.hasError) placeholderState else contentState,
                modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("benchmark-list")
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction(refreshLabel) {
                            if (uiState.isLoading || uiState.isRefreshing) false else {
                                onRefresh()
                                true
                            }
                        })
                    },
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(direction) + 16.dp,
                    end = padding.calculateEndPadding(direction) + 16.dp,
                    top = padding.calculateTopPadding() + 28.dp,
                    bottom = padding.calculateBottomPadding() + bottomOverlayPadding + 16.dp,
                ), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item("title") {
                    Column {
                        Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.benchmark_page_title), style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.benchmark_overview_notice), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AnimatedCatalogStatusNotice(notice, onRefresh, onDismissNotice,
                            updatedMessage = stringResource(R.string.benchmark_updated))
                    }
                }
                when {
                    uiState.isLoading -> item("loading") { BenchmarkMessage(loading = true) }
                    uiState.hasError -> item("error") { BenchmarkMessage(stringResource(R.string.benchmark_read_error), onRetry = onRetry) }
                    uiState.boards.isEmpty() -> item("empty") { BenchmarkMessage(stringResource(R.string.benchmark_empty)) }
                    else -> {
                        items(uiState.boards, key = { it.id }, contentType = { "benchmark" }) { board ->
                            BenchmarkCard(board, onClick = { onOpen(board.id) },
                                showVersion = board.name.trim().lowercase(Locale.ROOT) in repeatedNames)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BenchmarkLeaderboardScreen(
    uiState: BenchmarkUiState,
    boardId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val board = uiState.boards.find { it.id == boardId }
    val description = board?.description?.takeIf(String::isNotBlank)
    var showDescription by rememberSaveable(boardId) { mutableStateOf(false) }
    val contentState = rememberLazyListState()
    val placeholderState = rememberLazyListState()
    val sharedElementsEnabled by remember {
        derivedStateOf { contentState.firstVisibleItemIndex == 0 && contentState.firstVisibleItemScrollOffset == 0 }
    }
    val topModelIds = remember(board?.rankings) { board?.rankings.orEmpty().take(3).map { it.modelId }.toSet() }
    // 全榜固定一个坐标范围，不能根据 LazyColumn 当时组合的条目动态改变。
    val range = remember(board) {
        benchmarkChartRange(board?.rankings.orEmpty().mapNotNull { it.score }, board?.minScore, board?.maxScore)
    }
    Scaffold(modifier.testTag("benchmark-leaderboard"),
        containerColor = if (LocalBenchmarkSharedTransition.current != null) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back))
                }
                Text(stringResource(R.string.benchmark_leaderboard_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            LazyColumn(state = if (board != null && !uiState.isLoading && !uiState.hasError) contentState else placeholderState,
                modifier = Modifier.fillMaxSize().testTag("benchmark-leaderboard-list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when {
                    uiState.isLoading -> item("loading") { BenchmarkMessage(loading = true) }
                    uiState.hasError -> item("error") { BenchmarkMessage(stringResource(R.string.benchmark_read_error), onRetry = onRetry) }
                    board == null -> item("removed") { BenchmarkMessage(stringResource(R.string.benchmark_removed)) }
                    else -> {
                        item("identity") {
                            BenchmarkIdentity(board, onInfo = if (description != null) ({ showDescription = true }) else null,
                                sharedElementsEnabled = sharedElementsEnabled)
                        }
                        if (board.rankings.isNotEmpty()) {
                            items(board.rankings, key = { "rank:${it.modelId}" }, contentType = { "result" }) { entry ->
                                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                                    BenchmarkResultRow(entry, board.unit, range, modifier = Modifier.padding(16.dp),
                                        sharedBoardId = board.id.takeIf { sharedElementsEnabled && entry.modelId in topModelIds })
                                }
                            }
                        } else item("no-numeric") { BenchmarkMessage(stringResource(R.string.benchmark_no_numeric)) }
                    }
                }
            }
        }
    }
    if (showDescription && board != null && description != null && !uiState.isLoading && !uiState.hasError) {
        AlertDialog(
            onDismissRequest = { showDescription = false },
            title = { Text(board.name) },
            text = {
                Text(description, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.verticalScroll(rememberScrollState()).testTag("benchmark-description"))
            },
            confirmButton = {
                TextButton(onClick = { showDescription = false }) { Text(stringResource(R.string.benchmark_close_description)) }
            },
        )
    }
}

@Composable
private fun BenchmarkMessage(text: String = stringResource(R.string.benchmark_loading), loading: Boolean = false, onRetry: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator()
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.benchmark_retry)) }
        }
    }
}

// 合成成绩只用于设计预览，运行时仅使用本地发布包。
internal fun benchmarkPreviewBoard() = BenchmarkBoard(
    id = "preview", benchmarkId = "terminal-bench-v4", name = "Terminal-Bench", version = "4.0.0",
    metric = "task resolution rate", unit = "percent", category = "coding", description = null,
    higherIsBetter = true, minScore = null, maxScore = null,
    rankings = listOf(
        BenchmarkBoardEntry("a", "deepseek/a", "模型 A", "deepseek", 72.4, null, rank = 1),
        BenchmarkBoardEntry("b", "anthropic/b", "模型 B", "anthropic", 65.1, null, rank = 2),
        BenchmarkBoardEntry("c", "google/c", "模型 C", "google", 58.7, null, rank = 3),
    ),
)

private fun benchmarkPreviewLeaderboardBoard() = benchmarkPreviewBoard().copy(
    description = "模型在终端环境中完成真实软件工程任务的能力，按任务解决率排名。",
    rankings = listOf(
        BenchmarkBoardEntry("a", "deepseek/a", "模型 A", "deepseek", 72.4, null, rank = 1,
            reasoningEffort = "high", toolsEnabled = true),
        BenchmarkBoardEntry("b", "anthropic/b", "模型 B", "anthropic", 65.1, null, rank = 2,
            variant = "thinking", harness = "terminal-bench"),
        BenchmarkBoardEntry("c", "google/c", "模型 C", "google", 58.7, null, rank = 3,
            dataset = "v4-public", toolsEnabled = false),
    ),
)

@Preview(name = "Benchmarks · Light", showBackground = true)
@Preview(name = "Benchmarks · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Benchmarks · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun BenchmarkPreview() {
    ModelAtlasTheme(dynamicColor = false) { BenchmarkScreen(BenchmarkUiState(isLoading = false, boards = listOf(benchmarkPreviewBoard())), {}) }
}

@Preview(name = "Leaderboard · Light", showBackground = true)
@Preview(name = "Leaderboard · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Leaderboard · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun BenchmarkLeaderboardPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        BenchmarkLeaderboardScreen(
            uiState = BenchmarkUiState(isLoading = false, boards = listOf(benchmarkPreviewLeaderboardBoard())),
            boardId = "preview",
            onBack = {},
        )
    }
}
