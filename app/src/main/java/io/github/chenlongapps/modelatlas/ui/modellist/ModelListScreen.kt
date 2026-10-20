package io.github.chenlongapps.modelatlas.ui.modellist

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme

@Composable
fun ModelListRoute(
    modifier: Modifier = Modifier,
    viewModel: ModelListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ModelListScreen(uiState = uiState, onRetry = viewModel::retry, modifier = modifier)
}

@Composable
internal fun ModelListScreen(
    uiState: ModelListUiState,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    windowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
) {
    val layoutDirection = LocalLayoutDirection.current
    val backgroundColor = MaterialTheme.colorScheme.surfaceContainerLowest
    var showLicense by rememberSaveable { mutableStateOf(false) }
    // 展开状态提到列表层，避免滚动回收卡片后丢失，同时便于状态恢复。
    var expandedPriceModelId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("model-list")
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                end = innerPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "catalog-header") {
                CatalogHeader(uiState = uiState, onShowLicense = { showLicense = true })
            }
            when {
                uiState.isLoading -> item(key = "loading") {
                    CatalogStateCard(
                        title = stringResource(R.string.model_list_loading_title),
                        message = stringResource(R.string.model_list_loading),
                        isLoading = true,
                    )
                }
                uiState.error != null -> item(key = "error") {
                    val message = when (uiState.error) {
                        ModelListError.NETWORK -> R.string.model_list_network_error
                        ModelListError.SERVER -> R.string.model_list_server_error
                        ModelListError.INVALID_DATA -> R.string.model_list_invalid_data
                        ModelListError.RATE_LIMITED -> R.string.model_list_rate_limited
                        ModelListError.STORAGE -> R.string.model_list_storage_error
                    }
                    CatalogStateCard(
                        title = stringResource(R.string.model_list_error_title),
                        message = stringResource(message),
                        onRetry = onRetry,
                    )
                }
                uiState.models.isEmpty() -> item(key = "empty") {
                    CatalogStateCard(
                        title = stringResource(R.string.model_list_empty_title),
                        message = stringResource(R.string.model_list_empty),
                    )
                }
            }
            if (uiState.models.isNotEmpty()) {
                item(key = "sort-order") {
                    Text(
                        text = stringResource(R.string.model_list_order),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = uiState.models, key = { "model:${it.id}" }) { model ->
                ModelCard(
                    model = model,
                    priceDetailsExpanded = expandedPriceModelId == model.id,
                    onTogglePriceDetails = {
                        expandedPriceModelId =
                            if (expandedPriceModelId == model.id) null else model.id
                    },
                )
            }
            if (uiState.models.isNotEmpty()) {
                item(key = "price-notice") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.model_list_price_notice_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.model_list_price_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
    if (showLicense) {
        val resources = LocalResources.current
        val license = remember(resources) {
            resources.openRawResource(R.raw.models_dev_license).bufferedReader().use { it.readText() }
        }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.data_source_action)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.model_list_source_notice))
                    Text(stringResource(R.string.model_list_price_notice))
                    Text(license, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text(stringResource(R.string.close)) }
            },
        )
    }
}

@Composable
private fun CatalogHeader(uiState: ModelListUiState, onShowLicense: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog-header")
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (!uiState.isLoading && uiState.error == null) {
                    stringResource(R.string.model_list_count, uiState.models.size)
                } else {
                    stringResource(R.string.model_list_title)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onShowLicense) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.data_source_action),
            )
        }
    }
}

@Composable
private fun CatalogStateCard(
    title: String,
    message: String,
    isLoading: Boolean = false,
    onRetry: (() -> Unit)? = null,
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
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    } else {
                        Icon(painterResource(R.drawable.ic_catalog), contentDescription = null, modifier = Modifier.size(32.dp))
                    }
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

@Preview(name = "Loading", showBackground = true)
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
