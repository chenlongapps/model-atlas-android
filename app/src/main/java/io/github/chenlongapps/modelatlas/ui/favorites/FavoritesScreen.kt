package io.github.chenlongapps.modelatlas.ui.favorites

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.FavoriteIdentity
import io.github.chenlongapps.modelatlas.data.model.FavoriteModel
import io.github.chenlongapps.modelatlas.ui.model.LocalModelSharedTransition
import io.github.chenlongapps.modelatlas.ui.model.ModelIdentitySnapshot
import io.github.chenlongapps.modelatlas.ui.modellist.ModelCard
import io.github.chenlongapps.modelatlas.ui.modellist.labDisplayName
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

@Composable
internal fun FavoritesRoute(bottomOverlayPadding: Dp, onModelClick: (String) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FavoritesScreen(state, onModelClick, bottomOverlayPadding = bottomOverlayPadding, onRetry = viewModel::retry)
}

@Composable
internal fun FavoritesScreen(
    uiState: FavoritesUiState,
    onModelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomOverlayPadding: Dp = 0.dp,
    onRetry: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
) {
    val listState = rememberLazyListState()
    val statusState = rememberLazyListState()
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val direction = LocalLayoutDirection.current
    val shared = LocalModelSharedTransition.current
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    val ready = !uiState.isLoading && !uiState.hasError
    val removal = LocalFavoriteRemovalTransition.current
    val currentState by rememberUpdatedState(uiState)
    DisposableEffect(removal) {
        removal?.source = { id ->
            val state = currentState
            val index = state.favorites.indexOfFirst { it.identity.id == id }
            if (!state.isLoading && !state.hasError && index >= 0) RetainedFavorite(state.favorites[index], index) else null
        }
        onDispose { removal?.source = null }
    }
    val retained = removal?.retained
    val retainedId = retained?.favorite?.identity?.id
    val missingSource = retainedId != null && uiState.favorites.none { it.identity.id == retainedId }
    val opacity = remember(retainedId) { Animatable(1f) }
    LaunchedEffect(retainedId, removal?.awaitingReturn, missingSource, ready) {
        if (retainedId == null) return@LaunchedEffect
        if (!ready) {
            // 读取失败／重读仍遵守清除旧内容的规则，不能在恢复后重新闪现旧卡片。
            removal.clear(retainedId)
        } else if (!removal.awaitingReturn && missingSource) {
            opacity.animateTo(0f, tween(180))
            removal.clear(retainedId)
        }
    }
    val favorites = if (ready) removal?.displayFavorites(uiState.favorites) ?: uiState.favorites else emptyList()
    Scaffold(modifier.testTag("favorites-screen").drawWithContent {
        drawContent()
        val height = windowInsets.getTop(this) * 1.2f
        if (height > 0f) drawRect(Brush.verticalGradient(listOf(background.copy(alpha = 0.9f),
            background.copy(alpha = 0.6f), Color.Transparent), endY = height), size = Size(size.width, height))
    }, containerColor = background, contentWindowInsets = windowInsets) { padding ->
        LazyColumn(
            state = if (ready) listState else statusState,
            modifier = Modifier.fillMaxSize().testTag("favorites-list").consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + 16.dp,
                end = padding.calculateEndPadding(direction) + 16.dp,
                top = padding.calculateTopPadding() + 16.dp,
                bottom = padding.calculateBottomPadding() + bottomOverlayPadding + 16.dp,
            ), verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.nav_favorites), Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.favorites_order), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                uiState.isLoading -> item(key = "loading") { CircularProgressIndicator() }
                uiState.hasError -> item(key = "error") {
                    FavoriteStateCard {
                        Text(stringResource(R.string.favorites_read_error))
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
                favorites.isEmpty() -> item(key = "empty") {
                    FavoriteStateCard {
                        Text(stringResource(R.string.favorites_empty_title), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.favorites_empty))
                    }
                }
                else -> items(favorites, key = { "favorite:${it.identity.id}" }) { favorite ->
                    val isRetained = favorite.identity.id == retainedId
                    // 稳定 key 与完整原卡一直保留到共享转场结束；仅本次取消项禁用交互／读屏。
                    Box(Modifier.animateItem(fadeInSpec = null, placementSpec = tween(220), fadeOutSpec = null)
                        .graphicsLayer { alpha = if (isRetained) opacity.value else 1f }
                        .then(if (isRetained) Modifier.clearAndSetSemantics {}.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        } else Modifier)) {
                        val model = favorite.model
                        if (model != null) ModelCard(model, expandedId == model.id,
                            onTogglePriceDetails = { if (!isRetained) expandedId = if (expandedId == model.id) null else model.id },
                            onOpenDetail = if (isRetained) null else {
                                {
                                    shared?.captureIdentity(ModelIdentitySnapshot(model.id, model.name, model.lab, model.openWeights, model.license))
                                    onModelClick(model.id)
                                }
                            })
                        else Surface(
                            Modifier.fillMaxWidth().testTag("favorite-unavailable:${favorite.identity.id}")
                                .clickable(enabled = !isRetained, role = Role.Button, onClickLabel = stringResource(R.string.detail_open)) {
                                    onModelClick(favorite.identity.id)
                                }, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
                        ) {
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

@Composable
internal fun FavoriteHistoricalIdentity(identity: FavoriteIdentity) {
    Text(identity.name, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.model_list_lab, identity.lab?.let(::labDisplayName) ?: stringResource(R.string.no_data)),
        color = MaterialTheme.colorScheme.primary)
    Text(identity.id, style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.favorite_historical_identity), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FavoriteStateCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Preview(name = "Favorites · Light", showBackground = true)
@Preview(name = "Favorites · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Favorites · Large font", showBackground = true, widthDp = 320, fontScale = 2f)
@Composable
private fun FavoritesPreview() {
    ModelAtlasTheme(dynamicColor = false) {
        FavoritesScreen(FavoritesUiState(false, listOf(FavoriteModel(FavoriteIdentity("lab/model", "历史模型", "lab"), 0, null))), {})
    }
}
